package test.com.azaptree.actors.message

import scala.collection.immutable.TreeSet
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.math.Ordering
import scala.util.Success
import scala.util.Try
import org.scalatest.BeforeAndAfterAll
import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.MessageActor
import com.azaptree.actor.message.SUCCESS_MESSAGE_STATUS
import com.azaptree.actor.message.system.ApplicationMessageSupported
import com.azaptree.actor.message.system.ChildrenActorPaths
import com.azaptree.actor.message.system.GetActorConfig
import com.azaptree.actor.message.system.GetActorConfig
import com.azaptree.actor.message.system.GetChildrenActorPaths
import com.azaptree.actor.message.system.GetMessageStats
import com.azaptree.actor.message.system.GetMessageStats
import com.azaptree.actor.message.system.GetSystemMessageProcessorActorRef
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.IsApplicationMessageSupported
import com.azaptree.actor.message.system.MessageProcessedEvent
import com.azaptree.actor.message.system.MessageStats
import com.azaptree.actor.message.system.SystemMessage
import com.azaptree.actor.message.system.SystemMessageProcessor
import com.azaptree.actor.application.ActorRegistry
import com.azaptree.actor.application.ActorRegistry._
import com.typesafe.config.ConfigFactory
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.OneForOneStrategy
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.actor.SupervisorStrategy.Restart
import akka.actor.SupervisorStrategy.Resume
import akka.actor.UnhandledMessage
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.testkit.DefaultTimeout
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import com.azaptree.actor.application.ApplicationActor

object MessagingActorSpec {

  import akka.actor.SupervisorStrategy._

  val resumeStrategy = OneForOneStrategy(maxNrOfRetries = Int.MaxValue, withinTimeRange = Duration.Inf) {
    case e: IllegalStateException => Restart
    case _ => Resume
  }

  case object GetSupervisorStrategy

  class Printer extends MessageActor {

    override def receiveMessage = {
      case Message(msg: String, _) =>
        val path = self.path
        println(s"$path : msg = $msg")
      case Message(e: Exception, _) =>
        throw e
    }
  }

  class EchoMessageActor extends MessageActor {
    var printerActor: ActorRef = _

    override def preStart() = {
      super.preStart()
      val actorConfig = ActorConfig(classOf[Printer], context.self.path / "Printer")
      printerActor = actorConfig.actorOfActorContext
    }

    override def receiveMessage = {
      case message @ Message(msg: String, _) =>
        message.update(SUCCESS_MESSAGE_STATUS)
        tell(sender, message)
      case Message(e: Exception, _) =>
        throw e
      case Message(GetSupervisorStrategy, _) => tell(sender, Message[SupervisorStrategy](data = supervisorStrategy))
    }

  }

  class MessageLoggingTracker extends Actor with ActorLogging {
    var messageProcessedEventCount = 0
    var unhandledMessageCount = 0

    override def receive = {
      case msg: MessageProcessedEvent =>
        messageProcessedEventCount += 1
        log.info("{} : {}", messageProcessedEventCount, msg)
      case 'reset =>
        messageProcessedEventCount = 0
        unhandledMessageCount = 0
      case 'getCount => sender ! messageProcessedEventCount
      case 'getUnhandledMessage => sender ! unhandledMessageCount
      case m: UnhandledMessage =>
        unhandledMessageCount += 1
        log.info("UnhandledMessage : {}", m)
    }
  }

  def createActorSystem() = {
    val testConfig = ConfigFactory.parseString("""
        akka {
    		log-config-on-start = on
        
    		actor{
    			serialize-messages = on
    			serialize-creators = on
    		}
    	}
        """);

    ActorSystem("MessagingActorSpec", ConfigFactory.load(testConfig.withFallback(ConfigFactory.load())))
  }

}

class MessagingActorSpec(_system: ActorSystem) extends TestKit(_system)
    with DefaultTimeout with ImplicitSender
    with FeatureSpec with ShouldMatchers with BeforeAndAfterAll {

  def this() = this(MessagingActorSpec.createActorSystem())

  val messageLogger = system.actorOf(Props[MessagingActorSpec.MessageLoggingTracker], "MessageLoggingTracker")
  system.eventStream.subscribe(messageLogger, classOf[MessageProcessedEvent])
  system.eventStream.subscribe(messageLogger, classOf[UnhandledMessage])

  override def afterAll() = {
    system.shutdown()
  }

  val actorRegistryConfig = ActorConfig(actorClass = classOf[ActorRegistry], actorPath = system / ActorRegistry.ACTOR_NAME, topLevelActor = true)
  val actorRegistry = actorRegistryConfig.actorOfActorSystem

  import system.dispatcher
  Await.result(actorRegistry ? Message(HeartbeatRequest), 100 millis)

  val echoMessageActorConfig = ActorConfig(actorClass = classOf[MessagingActorSpec.EchoMessageActor], actorPath = system / "EchoMessageActor", topLevelActor = true)
  val echoMessageActor = echoMessageActorConfig.actorOfActorSystem

  val echoMessageActorWithResumeSupervisorStrategyConfig = ActorConfig(actorClass = classOf[MessagingActorSpec.EchoMessageActor],
    actorPath = system / "EchoMessageActorWithResumeSupervisorStrategy",
    supervisorStrategy = Right(MessagingActorSpec.resumeStrategy),
    topLevelActor = true)
  val echoMessageActorWithResumeSupervisorStrategy = echoMessageActorWithResumeSupervisorStrategyConfig.actorOfActorSystem

  val appActorConfig = ActorConfig(actorClass = classOf[ApplicationActor],
    actorPath = system / "Application",
    topLevelActor = true,
    config = Some(ConfigFactory.parseString("""
        app{
    		name = "MessagingActorSpec"        
    		version = "0.0.1-SNAPSHOT"
    	}
        """)))
  val appActor = appActorConfig.actorOfActorSystem

  def getMessageActorStats(actor: ActorRef): MessageStats = {
    val messageStatsFuture = ask(actor, Message[GetMessageStats.type](GetMessageStats)).mapTo[Message[MessageStats]]
    implicit val dispatcher = system.dispatcher
    Await.result(messageStatsFuture, 100 millis).data
  }

  def getEchoMessageActorSupervisorStrategy(actor: ActorRef): SupervisorStrategy = {
    implicit val dispatcher = system.dispatcher
    val future = ask(actor, Message[MessagingActorSpec.GetSupervisorStrategy.type](MessagingActorSpec.GetSupervisorStrategy)).mapTo[Message[SupervisorStrategy]]
    Await.result(future, 100 millis).data
  }

  def log(actors: Set[ActorRef]) = {
    println(actors.foldLeft("\n")((s, a) => s + "\n" + a.path) + "\n")

    actors.foreach {
      actor =>
        val actors2 = Await.result(ask(actorRegistry, Message(ActorRegistry.GetRegisteredActors(Some(actor.path)))).mapTo[Message[ActorRegistry.RegisteredActors]], 100 millis).data.actors
        assert(actors2.contains(actor), "actor is expected in RegisteredActors response: " + actor.path)

        var sortedActors = TreeSet[ActorRef]()
        sortedActors = sortedActors ++ actors2
        println(sortedActors.foldLeft("*** " + actor.path + " ***")((s, a) => s + "\n   |--" + a.path) + "\n")
    }
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
      supervisorStrategy should be(MessagingActorSpec.resumeStrategy)

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
      actorConfig.data.name should be(this.echoMessageActorConfig.name)
    }
  }

  feature("An Actor will log all messages that are received with processing metrics") {
    scenario("Send an Actor some application messages and check that they are logged.") {
      implicit val dispatcher = system.dispatcher

      messageLogger ! 'reset
      import akka.pattern._

      Await.result(ask(messageLogger, 'getCount).mapTo[Int], 100 millis) should be(0)

      val echoedMessage = ask(echoMessageActor, Message("An Actor will log all messages that are received with processing metrics")).mapTo[Message[String]]
      Await.result(echoedMessage, 100.millis)
      Thread.sleep(10l);

      Await.result(ask(messageLogger, 'getCount).mapTo[Int], 100 millis) should be(1)

      for (i <- 1 to 10) {
        echoMessageActor ! Message(i.toString)
      }
      Thread.sleep(20l);

      Await.result(ask(messageLogger, 'getCount).mapTo[Int], 100 millis) should be(11)
    }

    scenario("Send an Actor some system messages and check that they are not logged.") {
      messageLogger ! 'reset

      for (i <- 1 to 10) { echoMessageActor ! Message(s"HELLO - $i") }
      Thread.sleep(20l);
      Await.result(ask(messageLogger, 'getCount).mapTo[Int], 100 millis) should be(10)

      for (i <- 1 to 10) { echoMessageActor ! Message(HeartbeatRequest) }
      Thread.sleep(10l);
      Await.result(ask(messageLogger, 'getCount).mapTo[Int], 100 millis) should be(10)
    }
  }

  feature("""The ActorPath's for a MessageActor's children can be retrieved """) {
    scenario("Send a GetChildrenActorPaths to a MessageActor.") {
      val future = ask(echoMessageActor, Message(GetChildrenActorPaths)).mapTo[Message[ChildrenActorPaths]]
      val response = Await.result(future, 100 millis)
      response.data.actorPaths.filter(_.name == "Printer").isEmpty should be(false)
    }
  }

  feature("""Messages that are of not type com.azaptree.actor.message.Message will submit an UnhandledMessage event to the ActorSystem event stream""") {
    scenario("Send an invalid message and check that an UnhandledMessage event was published") {
      messageLogger ! 'reset
      Thread.sleep(5l)
      echoMessageActor ! "INVALID MESSAGE"
      Thread.sleep(10l)
      Await.result(ask(messageLogger, 'getUnhandledMessage).mapTo[Int], 100 millis) should be(1)
    }
  }

  feature("The MessageActor/systemMessageProcessor can be obtained from the MessageActor") {
    scenario("Request the systemMessageProcessor ActorRef and send it some SystemMessages") {
      val response = Await.result(ask(echoMessageActor, Message(GetSystemMessageProcessorActorRef)).mapTo[Message[SystemMessageProcessor]], 100 millis)
      val sysMsgProcessor = response.data.actorRef
      Await.result(ask(sysMsgProcessor, Message(HeartbeatRequest)).mapTo[Message[HeartbeatRequest.type]], 10 millis)
    }
  }

  feature("You are able to check with the MessageActor/systemMessageProcessor whether or not an application message type is supported by the MessageActor") {
    scenario("Check for some messages that are supported") {
      val response = Await.result(ask(echoMessageActor, Message(GetSystemMessageProcessorActorRef)).mapTo[Message[SystemMessageProcessor]], 100 millis)
      val sysMsgProcessor = response.data.actorRef
      val appMsgSupportedResponse = Await.result(ask(sysMsgProcessor, Message(IsApplicationMessageSupported(Message[String]("IS SUPPORTED")))).mapTo[Message[ApplicationMessageSupported]], 10 millis)
      appMsgSupportedResponse.data.supported should be(true)
    }

    scenario("Check for some messages that are not supported") {
      val response = Await.result(ask(echoMessageActor, Message(GetSystemMessageProcessorActorRef)).mapTo[Message[SystemMessageProcessor]], 100 millis)
      val sysMsgProcessor = response.data.actorRef
      val appMsgSupportedResponse = Await.result(ask(sysMsgProcessor, Message(IsApplicationMessageSupported(Message[Long](200l)))).mapTo[Message[ApplicationMessageSupported]], 10 millis)
      appMsgSupportedResponse.data.supported should be(false)

      echoMessageActor ! Message("Valid Message")
      val statsBefore = getMessageActorStats(echoMessageActor)
      echoMessageActor ! Message(200l)
      val statsAfter = getMessageActorStats(echoMessageActor)

      statsAfter.messageCount should be(statsBefore.messageCount + 1)
      statsAfter.messageFailedCount should be(statsBefore.messageFailedCount + 1)
      statsAfter.lastMessageProcessedOn should be(statsBefore.lastMessageProcessedOn)
    }
  }

  feature("""Actors will register with an ActorRegistry when started/restarted and unregister when terminated""") {
    scenario("Create an Actor and check that is has registered. Then stop the actor and check that is has unregistered") {
      val actorRegistry = system.actorFor(system / ActorRegistry.ACTOR_NAME)
      val registeredActorsFuture = ask(actorRegistry, Message(ActorRegistry.GetRegisteredActors())).mapTo[Message[ActorRegistry.RegisteredActors]]

      val actors = Await.result(registeredActorsFuture, 100 millis).data.actors
      log(actors)
      actors.size should be > (0)

      val actorConfig = ActorConfig(actorClass = classOf[MessagingActorSpec.EchoMessageActor], actorPath = system / "ActorRegistryTest")
      val actor = actorConfig.actorOfActorSystem
      Await.result(actor ? Message(HeartbeatRequest), 100 millis)
      Await.result(system.actorFor(system / actorConfig.name / "Printer") ? Message(HeartbeatRequest), 100 millis)

      Thread.sleep(10l)

      val registeredActors2 = Await.result(ask(actorRegistry, Message(ActorRegistry.GetRegisteredActors())).mapTo[Message[ActorRegistry.RegisteredActors]], 10 millis).data.actors
      log(registeredActors2)
      registeredActors2.size should be(actors.size + 2)

      system.stop(actor)
      Thread.sleep(10l)
      val registeredActorsAfterStoppingTestActor = Await.result(ask(actorRegistry, Message(ActorRegistry.GetRegisteredActors())).mapTo[Message[ActorRegistry.RegisteredActors]], 100 millis).data.actors
      registeredActorsAfterStoppingTestActor.size should be(actors.size)
    }
  }

  feature("""From an ApplicationActor you can obtain the app name and version that is deployed""") {

    scenario("Submit a GetApplicationInfo request and check that a ApplicationInfo response is returned") {
      import ApplicationActor._
      val appInfo = Await.result(ask(appActor, Message(GetApplicationInfo)).mapTo[Message[ApplicationInfo]], 100 millis).data
      println(s"\nappInfo = $appInfo\n")

      appInfo.appName should be(appActorConfig.config.get.getString(ApplicationActor.ConfigKeys.APP_NAME))
      appInfo.appVersion should be(appActorConfig.config.get.getString(ApplicationActor.ConfigKeys.APP_VERSION))
    }

  }

  feature("""From an ApplicationActor you can obtain JVM runtime info""") {

    scenario("Submit a GetJvmInfo request and check that a Seq[JvmInfo]  response is returned") {
      import ApplicationActor._
      val response = Await.result(ask(appActor, Message(GetJvmInfo(RuntimeInfoRequest :: ClassLoadingInfoRequest :: Nil))).mapTo[Message[Seq[JvmInfo]]], 100 millis).data
      println(s"\n response = $response\n")

      response.size should be(2)
      response(0).isInstanceOf[RuntimeInfo] should be(true)
      response(1).isInstanceOf[ClassLoadingInfo] should be(true)
    }

  }

}
