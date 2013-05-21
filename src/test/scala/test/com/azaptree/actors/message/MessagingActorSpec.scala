package test.com.azaptree.actors.message

import scala.concurrent.duration._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.system.GetMessageStats
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.MessageStats
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern._
import akka.testkit.DefaultTimeout
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import com.azaptree.actor.message.Message
import scala.concurrent.Await
import com.azaptree.actor.message.MessageActor
import com.azaptree.actor.message.MessageProcessor
import akka.actor.SupervisorStrategy
import akka.actor.OneForOneStrategy
import akka.actor.OneForOneStrategy
import akka.actor.ActorRef

object ActorSpec {

  import akka.actor.SupervisorStrategy._

  val resumeStrategy = OneForOneStrategy(maxNrOfRetries = Int.MaxValue, withinTimeRange = Duration.Inf) {
    case _ => Resume
  }

  case object GetSupervisorStrategy

  class EchoMessageActor(actorConfig: ActorConfig, supervisionStrategy: SupervisorStrategy = SupervisorStrategy.defaultStrategy) extends MessageActor(actorConfig) {

    override val supervisorStrategy = supervisionStrategy

    override protected[this] def processMessage(messageData: Any)(implicit message: Message[_]) = {
      import com.azaptree.actor.message._
      messageData match {
        case msg: String =>
          message.update(SUCCESS_MESSAGE_STATUS)
          tell(sender, message)
        case e: Exception =>
          throw e
        case GetSupervisorStrategy => tell(sender, Message[SupervisorStrategy](data = supervisorStrategy))
      }
    }
  }

  class EchoMessageActor2(actorConfig: ActorConfig) extends MessageActor(actorConfig) {

    override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = Int.MaxValue) {
      case _ => Resume
    }

    override protected[this] def processMessage(messageData: Any)(implicit message: Message[_]) = {
      import com.azaptree.actor.message._
      messageData match {
        case msg: String =>
          message.update(SUCCESS_MESSAGE_STATUS)
          tell(sender, message)
        case e: Exception =>
          throw e
        case GetSupervisorStrategy => tell(sender, Message[SupervisorStrategy](data = supervisorStrategy))
      }
    }
  }
}

class ActorSpec(_system: ActorSystem) extends TestKit(_system)
    with DefaultTimeout with ImplicitSender
    with FeatureSpec with ShouldMatchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("ActorSpec"))

  override def afterAll() = {
    system.shutdown()
  }

  val actorConfig = ActorConfig("EchoMessageActor")
  val echoMessageActor = system.actorOf(Props(new ActorSpec.EchoMessageActor(actorConfig)), actorConfig.name)

  val actorConfig2 = ActorConfig("echoMessageActorWithResumeSupervisorStrategy")
  val echoMessageActorWithResumeSupervisorStrategy = system.actorOf(Props(new ActorSpec.EchoMessageActor(actorConfig2, ActorSpec.resumeStrategy)), actorConfig2.name)

  def getEchoMessageActorStats(actor: ActorRef): MessageStats = {
    val messageStatsFuture = ask(actor, Message[GetMessageStats.type](GetMessageStats)).mapTo[Message[MessageStats]]
    implicit val dispatcher = system.dispatcher
    Await.result(messageStatsFuture, 100 millis).data
  }

  def getEchoMessageActorSupervisorStrategy(actor: ActorRef): SupervisorStrategy = {
    val future = ask(actor, Message[ActorSpec.GetSupervisorStrategy.type](ActorSpec.GetSupervisorStrategy)).mapTo[Message[SupervisorStrategy]]
    implicit val dispatcher = system.dispatcher
    Await.result(future, 100 millis).data
  }

  feature("""Actors will keep track of counts for total number of messages processed successfully and messages processed unsucessfully. 
      | Actors will also track the last time a message was processed successfully, and the last time a message processing failure occurred.
      | When an Actor receives a Message[GetStats] message, it will return a Message[MessageStats] to the sender.
      | Messages of type com.azaptree.actor.message.system.SystemMessage do not count against MessageStats. 
      | The last time a heartbeat message was received will be tracked.""".stripMargin) {

    scenario("""Create a new Actor and send some application messages. 
        |Then check that number of messages successfully processed matches the number of application messages that were sent
        |Verify that lastMessageProcessedOn has been updated.""".stripMargin) {

      val msgStatsBefore = getEchoMessageActorStats(echoMessageActor)

      val request = Message[String]("CIAO MUNDO!")

      var lastMessageSentTime = System.currentTimeMillis();
      val requestCount = 10
      for (i <- 1 to requestCount) {
        lastMessageSentTime = System.currentTimeMillis();
        echoMessageActor ! request
        expectMsgPF(100.millis) {
          case msg: Message[_] =>
            msg.data match {
              case text: String =>
                assert(request.data == text)
            }
        }
      }

      echoMessageActor ! Message[GetMessageStats.type](GetMessageStats)
      expectMsgPF(100.millis) {
        case msg: Message[_] =>
          msg.data match {
            case msgStats: MessageStats =>
              msgStats.messageCount should be(msgStatsBefore.messageCount + requestCount)
              msgStats.lastMessageReceivedOn.get should be >= (lastMessageSentTime)
              msgStats.lastMessageProcessedOn.get should be >= (msgStats.lastMessageReceivedOn.get)
          }
      }

    }

    scenario("""Create a new Actor and send some Application Messages followed by Hearbeat messages. 
        | Then check that number of messages successfully processed has not been incremented.
        | Verify that lastHeartbeatOn has been updated.""".stripMargin) {

      val msgStatsBefore = getEchoMessageActorStats(echoMessageActor)

      val request = Message[String]("CIAO MUNDO!")

      var lastMessageSentTime = System.currentTimeMillis();
      val requestCount = 10
      for (i <- 1 to requestCount) {
        lastMessageSentTime = System.currentTimeMillis();
        echoMessageActor ! request
        expectMsgPF(100.millis) {
          case msg: Message[_] =>
            msg.data match {
              case text: String =>
                assert(request.data == text)
            }
        }
      }

      echoMessageActor ! Message[GetMessageStats.type](GetMessageStats)
      val msgStats1 = expectMsgPF(100.millis) {
        case msg: Message[_] =>
          msg.data match {
            case msgStats: MessageStats =>
              msgStats.messageCount should be(msgStatsBefore.messageCount + requestCount)
              msgStats.lastMessageReceivedOn.get should be >= (lastMessageSentTime)
              msgStats
          }
      }

      Thread.sleep(10l)
      echoMessageActor ! Message[HeartbeatRequest.type](HeartbeatRequest)
      expectMsgPF(100.millis) {
        case msg: Message[_] =>
          msg.data match {
            case response @ HeartbeatResponse =>
              println(response)
          }
      }

      echoMessageActor ! Message[GetMessageStats.type](GetMessageStats)
      expectMsgPF(100.millis) {
        case msg: Message[_] =>
          msg.data match {
            case msgStats: MessageStats =>
              msgStats.messageCount should be(msgStats1.messageCount)
              msgStats.lastMessageReceivedOn should be(msgStats1.lastMessageReceivedOn)
              msgStats.lastHeartbeatOn should be > (msgStats1.lastMessageReceivedOn)
          }
      }
    }

    scenario("""Create a new Actor and send some messages that will trigger failures. 
        |The actor has an Supervision policy to restart it. Thus the MessageStats should be reset after the failure""".stripMargin) {

      val now = System.currentTimeMillis()
      echoMessageActor ! Message[Exception](data = new Exception("Testing Message Failure"))
      val msgStats = getEchoMessageActorStats(echoMessageActor)

      msgStats.actorCreatedOn should be >= now
      msgStats.messageCount should be(0)
      msgStats.lastHeartbeatOn should be(None)
      msgStats.lastMessageProcessedOn should be(None)
      msgStats.lastMessageReceivedOn should be(None)

    }

    scenario("""Create a new Actor and send some messages that will trigger failures. 
        |The Actor should have supervision strategy to resume. 
        |Thus, the message count should have increased, but lastMessageProcessedOn should not have changed.""".stripMargin) {

      pending

      //      val msgStatsBefore = getEchoMessageActorStats(echoMessageActorWithResumeSupervisorStrategy)
      //      val supervisorStrategy = getEchoMessageActorSupervisorStrategy(echoMessageActorWithResumeSupervisorStrategy)
      //      println(s"echoMessageActorWithResumeSupervisorStrategy.supervisorStrategy = $supervisorStrategy")
      //      supervisorStrategy should be(ActorSpec.resumeStrategy)
      //
      //      val request = Message[String]("CIAO MUNDO!")
      //
      //      val requestCount = 10
      //      for (i <- 1 to requestCount) {
      //        echoMessageActorWithResumeSupervisorStrategy ! request
      //        expectMsgPF(100.millis) {
      //          case msg: Message[_] =>
      //            msg.data match {
      //              case text: String =>
      //                assert(request.data == text)
      //            }
      //        }
      //      }
      //
      //      val now = System.currentTimeMillis()
      //      Thread.sleep(10l)
      //
      //      val msgStatsBefore2 = getEchoMessageActorStats(echoMessageActorWithResumeSupervisorStrategy)
      //      println(s"*** msgStatsBefore2 = $msgStatsBefore2")
      //
      //      echoMessageActorWithResumeSupervisorStrategy ! Message[Exception](data = new Exception("Testing Message Failure"))
      //
      //      val msgStats = getEchoMessageActorStats(echoMessageActorWithResumeSupervisorStrategy)
      //      println(s"*** msgStats = $msgStats")
      //
      //      msgStats.actorCreatedOn should be(msgStatsBefore2.actorCreatedOn)
      //      msgStats.messageCount should be(msgStatsBefore2.messageCount + 1)
      //      msgStats.lastHeartbeatOn should be(msgStatsBefore2.lastHeartbeatOn)
      //      msgStats.lastMessageProcessedOn should be(msgStatsBefore2.lastMessageProcessedOn)
      //      msgStats.lastMessageReceivedOn should be > msgStatsBefore2.lastMessageReceivedOn

    }

  }

  feature("""The ActorConfig can be requested by sending a message to the Actor""") {
    scenario("Send an Actor a GetConfig message") {
      pending
    }
  }

  feature("An Actor will log all messages that are received with processing metrics") {
    scenario("Send an Actor some application messages and check that they are logged.") {
      pending
    }

    scenario("Send an Actor some system messages and check that they are logged.") {
      pending
    }
  }

  feature("An ActorSystem can be configured to log to a database") {
    scenario("Send an Actor some messages, then try to find them in the database") {
      pending
    }
  }

  feature("An ActorSystem can be configured to log all DeadLetters") {
    scenario("Send a msg to a non-existent Actor, which will cause the message to be sent to published as a DeadLetter") {
      pending
    }

    scenario("The ActorSystem is configured to log the dead letters to a database") {
      pending
    }
  }

  feature("Actors will publish MessageProcessedEvents to the ActorSystem's event stream") {
    scenario("""Create an Actor that subscribes to MessageEvents. 
        |Then send messages to another Actor, and check that MessageEvent's are published for each message """.stripMargin) {
      pending
    }
  }

  feature("""Actors will register with an ActorRegistry as when started/restarted and unregister when stopped""") {
    scenario("Create an Actor and check that is has registered") {
      pending
    }

    scenario("Restart an Actor and check that is has unregistered and registered") {
      pending
    }

    scenario("Stop an Actor and check that is has unregistered") {
      pending
    }
  }

  feature("The mailbox size, i.e., the number of messages queued in a mailbox, can be inspected at runtime") {
    scenario("""1. Create an Actor with Stash, and send it messages. 
        |2. Check the mailbox size. 
        |3. Send the Actor a message to unstash the messages. Then confirm that the mailbox has been flushed.""".stripMargin) {
      pending
    }
  }

  feature("An Actor defines the messages it supports within its companion object") {
    scenario("Send messages to the Actor using message types defined within its companion object") {
      pending
    }

  }

}
