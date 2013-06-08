package test.com.azaptree.actor.component

import scala.collection.immutable.TreeSet
import scala.collection.immutable.VectorBuilder
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import org.scalatest.BeforeAndAfterAll
import org.scalatest.FeatureSpec
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import com.azaptree.actor.application.ActorRegistry
import com.azaptree.actor.application.ApplicationActor
import com.azaptree.actor.component.ActorSystemComponent
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.config.ActorConfigRegistry
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.MessageActor
import com.azaptree.actor.message.SUCCESS_MESSAGE_STATUS
import com.azaptree.actor.message.system.MessageProcessedEvent
import com.typesafe.config.ConfigFactory

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.actor.SupervisorStrategy.Restart
import akka.actor.SupervisorStrategy.Resume
import akka.actor.UnhandledMessage
import akka.actor.actorRef2Scala
import akka.util.Timeout

object Actors {
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
}

object ActorSystemComponentConfig {

  implicit val testConfig = ConfigFactory.parseString("""
        akka {
    		log-config-on-start = on
        
    		actor{
    			serialize-messages = on
    			serialize-creators = on
    		}
    	}
        """);

  import Actors._

  implicit val createActorConfigs: ActorSystem => Iterable[ActorConfig] = system => {
    var actorConfigs = new VectorBuilder[ActorConfig]()
    actorConfigs += ActorConfig(actorClass = classOf[EchoMessageActor], actorPath = system / "EchoMessageActor", topLevelActor = true)
    actorConfigs += ActorConfig(actorClass = classOf[EchoMessageActor],
      actorPath = system / "EchoMessageActorWithResumeSupervisorStrategy",
      supervisorStrategy = Right(resumeStrategy),
      topLevelActor = true)
    actorConfigs += ActorConfig(actorClass = classOf[ApplicationActor],
      actorPath = system / "Application",
      topLevelActor = true,
      config = Some(ConfigFactory.parseString("""
        app{
    		name = "MessagingActorSpec"        
    		version = "0.0.1-SNAPSHOT"
    	}
        """)))
    actorConfigs.result
  }

}

class ActorSystemComponentSpec extends FunSpec with ShouldMatchers with BeforeAndAfterAll {

  import ActorSystemComponentConfig._

  val actorSystemComponent = ActorSystemComponent("ActorSystemComponentTest")

  val actorSystemComponentInstanceStarted = actorSystemComponent.startup()

  val actorSystem = actorSystemComponentInstanceStarted.actorSystem

  override def afterAll() = {
    actorSystemComponent.shutdown(actorSystemComponentInstanceStarted)
  }

  import akka.pattern.ask
  import scala.concurrent.duration._

  implicit val defaultTimeout = new Timeout(1 second)

  def log(actors: Set[ActorRef])(implicit actorRegistry: ActorRef) = {
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

  describe("An ActorSystemComponent") {
    it("will register the ActorRegistry automatically before other actors are created") {
      info("checks that the ActorConfig is registered for the ActorRegistry Actor")
      val actorRegistryConfig = ActorConfigRegistry.getActorConfig(actorSystem.name, actorSystem / ActorRegistry.ACTOR_NAME).get

      info("Verify that the ActorRegistry actor exists")
      implicit val actorRegistry = actorSystemComponentInstanceStarted.actorRegistryActor
      val registeredActorsFuture = ask(actorRegistry, Message(ActorRegistry.GetRegisteredActors())).mapTo[Message[ActorRegistry.RegisteredActors]]

      val actors = Await.result(registeredActorsFuture, 100 millis).data.actors
      log(actors)
      info("Check that the number of actors registered equals the number of ActorConfigs that are registered")
      actors.size should be > (ActorConfigRegistry.actorPaths(actorSystem.name).size)
    }
  }

}