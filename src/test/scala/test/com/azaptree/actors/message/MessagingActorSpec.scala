package test.com.azaptree.actors.message

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import org.scalatest.BeforeAndAfterAll
import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers

import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.message._
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.MessageActor
import com.azaptree.actor.message.MessageProcessor
import com.azaptree.actor.message.system.GetActorConfig
import com.azaptree.actor.message.system.GetMessageStats
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.MessageStats

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.OneForOneStrategy
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.pattern._
import akka.testkit.DefaultTimeout
import akka.testkit.ImplicitSender
import akka.testkit.TestKit

object ActorSpec {

  import akka.actor.SupervisorStrategy._

  val resumeStrategy = OneForOneStrategy(maxNrOfRetries = Int.MaxValue, withinTimeRange = Duration.Inf) {
    case e: IllegalStateException => Restart
    case _ => Resume
  }

  case object GetSupervisorStrategy

  class Printer(actorConfig: ActorConfig) extends MessageActor(actorConfig) {

    messageProcessingBuilder += {
      case Message(msg: String, _) =>
        val path = self.path
        println(s"$path : msg = $msg")
      case Message(e: Exception, _) =>
        throw e
    }
  }

  class EchoMessageActor(actorConfig: ActorConfig) extends MessageActor(actorConfig) {
    var printerActor: ActorRef = _

    override def preStart() = {
      super.preStart()
      val actorConfig = ActorConfig("Printer")
      printerActor = context.actorOf(Props(new Printer(actorConfig)), actorConfig.name)
    }

    messageProcessingBuilder += {
      case message @ Message(msg: String, _) =>
        message.update(SUCCESS_MESSAGE_STATUS)
        tell(sender, message)
      case Message(e: Exception, _) =>
        throw e
      case Message(GetSupervisorStrategy, _) => tell(sender, Message[SupervisorStrategy](data = supervisorStrategy))
    }

  }

  //  class MessageLoggingTracker(actorConfig:ActorConfig)extends MessageActor(actorConfig){
  //    
  //  }

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

  val echoMessageActorWithResumeSupervisorStrategyConfig = ActorConfig(name = "echoMessageActorWithResumeSupervisorStrategy", supervisorStrategy = Some(ActorSpec.resumeStrategy))
  val echoMessageActorWithResumeSupervisorStrategy = system.actorOf(Props(new ActorSpec.EchoMessageActor(echoMessageActorWithResumeSupervisorStrategyConfig)), echoMessageActorWithResumeSupervisorStrategyConfig.name)

  def getMessageActorStats(actor: ActorRef): MessageStats = {
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
      | Actors will track the last time a message was processed successfully.
      | When an Actor receives a Message[GetStats] message, it will return a Message[MessageStats] to the sender.
      | Messages of type com.azaptree.actor.message.system.SystemMessage do not count against MessageStats. 
      | The last time a heartbeat message was received will be tracked.
      | Message failures are tracked, but will only be returned in a MessageStats response message (in reply to a GetStats request) if the SupervisorStrategy returns a Resume directive""".stripMargin) {

    scenario("""Create a new Actor and send some application messages. 
        |Then check that number of messages successfully processed matches the number of application messages that were sent
        |Verify that lastMessageProcessedOn has been updated.""".stripMargin) {

      val msgStatsBefore = getMessageActorStats(echoMessageActor)

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

      val msgStatsBefore = getMessageActorStats(echoMessageActor)

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

      val requestCount = 10
      for (i <- 1 to requestCount) {
        echoMessageActor ! Message[String]("CIAO MUNDO #" + i)
      }

      val now = System.currentTimeMillis()
      Thread.sleep(10l)
      echoMessageActor ! Message[Exception](data = new Exception("Testing Message Failure"))
      val msgStats = getMessageActorStats(echoMessageActor)

      msgStats.actorCreatedOn should be >= now
      msgStats.messageCount should be(0)
      msgStats.lastHeartbeatOn should be(None)
      msgStats.lastMessageProcessedOn should be(None)
      msgStats.lastMessageReceivedOn should be(None)

    }

    scenario("""Create a new Actor and send some messages that will trigger failures. 
        |The Actor should have supervision strategy to resume. 
        |Thus, the message count should have increased, but lastMessageProcessedOn should not have changed.""".stripMargin) {

      val supervisorStrategy = getEchoMessageActorSupervisorStrategy(echoMessageActorWithResumeSupervisorStrategy)
      println(s"echoMessageActorWithResumeSupervisorStrategy.supervisorStrategy = $supervisorStrategy")
      supervisorStrategy should be(ActorSpec.resumeStrategy)

      val printer = system.actorFor("akka://%s/user/%s/Printer".format(system.name, echoMessageActorWithResumeSupervisorStrategyConfig.name))
      val msgStatsBefore = getMessageActorStats(printer)

      val requestCount = 10
      for (i <- 1 to requestCount) {
        printer ! Message[String]("CIAO MUNDO #" + i)
      }

      val now = System.currentTimeMillis()
      Thread.sleep(10l)

      val msgStatsBefore2 = getMessageActorStats(printer)
      println(s"*** msgStatsBefore2 = $msgStatsBefore2")
      Thread.sleep(10l)
      printer ! Message[Exception](data = new Exception("Testing Message Failure"))

      val msgStats = getMessageActorStats(printer)
      println(s"*** msgStats = $msgStats")

      msgStats.actorCreatedOn should be(msgStatsBefore2.actorCreatedOn)
      msgStats.messageCount should be(msgStatsBefore2.messageCount + 1)
      msgStats.lastHeartbeatOn should be(msgStatsBefore2.lastHeartbeatOn)
      msgStats.lastMessageProcessedOn should be(msgStatsBefore2.lastMessageProcessedOn)
      msgStats.lastMessageReceivedOn should be > msgStatsBefore2.lastMessageReceivedOn
      msgStats.lastMessageFailedOn should be >= msgStats.lastMessageReceivedOn
      msgStats.messageFailedCount should be(msgStatsBefore2.messageFailedCount + 1)
    }

  }

  feature("""The ActorConfig can be requested by sending a message to the Actor""") {
    scenario("Send an Actor a GetConfig message") {
      import akka.pattern._
      val future = ask(echoMessageActor, Message[GetActorConfig.type](data = GetActorConfig)).mapTo[Message[ActorConfig]]
      val actorConfig = Await.result(future, 100.millis)
      actorConfig.data.name should be(this.actorConfig.name)
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
