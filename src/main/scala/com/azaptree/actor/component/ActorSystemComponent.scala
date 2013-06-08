package com.azaptree.actor.component

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import com.azaptree.actor.application.ActorRegistry
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.config.ActorConfigRegistry
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.application.Component
import com.azaptree.application.lifecycle.LifeCycle._
import com.typesafe.config.Config

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout

case class ActorSystemComponent(
  override val name: String,
  override val instance: Option[ActorSystem] = None,
  override val state: State = NotConstructed)(
    implicit config: Config, createActorConfigs: ActorSystem => Iterable[ActorConfig])
    extends Component[ActorSystem] {

  override protected def create(): ActorSystemComponent = {
    val actorSystem = ActorSystem(name, config)
    copy(instance = Some(actorSystem), state = Constructed)
  }

  override protected def init(): ActorSystemComponent = {
    def createActorRegistryActor(actorSystem: ActorSystem) = {
      val actorRegistryConfig = ActorConfig(actorClass = classOf[ActorRegistry],
        topLevelActor = true,
        actorPath = actorSystem / ActorRegistry.ACTOR_NAME)
      val actorRegistry = actorRegistryConfig.actorOfActorSystem(actorSystem)

      implicit val askTimeout = Timeout(1 second)
      val reply = actorRegistry ? Message(HeartbeatRequest)
      Await.result(reply, 1 second)
    }

    createActorRegistryActor(instance.get)
    copy(state = Initialized)
  }

  override protected def start(): ActorSystemComponent = {
    implicit val actorSystem = instance.get
    val actorConfigs = createActorConfigs(actorSystem)
    actorConfigs.foreach(ActorConfigRegistry.register(actorSystem.name, _))
    actorConfigs.filter(actorConfig => actorConfig.topLevelActor).foreach(_.actorOfActorSystem)
    copy(state = Started)
  }

  override protected def stop(): ActorSystemComponent = {
    state match {
      case Started =>
        val actorSystem = instance.get
        actorSystem.shutdown()
        while (!actorSystem.isTerminated) {
          logger.info("Waiting for ActorSystem to shutdown : {}", name)
          try {
            actorSystem.awaitTermination(30 second)
          } catch {
            case e: TimeoutException => // ignore
          }
        }

        copy(state = Stopped, instance = None)
      case Stopped =>
        logger.info("Component is already stopped")
        this
      case _ => throw new IllegalStateException(s"Trying to stop the component while in state [{$state}] is invalid")
    }

  }

}