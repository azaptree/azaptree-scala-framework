package com.azaptree.actor.component

import com.azaptree.application.Component
import com.azaptree.application.ComponentInstance
import com.azaptree.application.lifecycle._
import com.typesafe.config.Config
import akka.actor.ActorSystem
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.application.ActorRegistry
import akka.pattern._
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.system.HeartbeatRequest
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.Await
import com.azaptree.actor.config.ActorConfigRegistry
import java.util.concurrent.TimeoutException

case class ActorSystemComponentInstance[S <: State](actorSystem: ActorSystem) extends ComponentInstance[S] {
  type A = ActorSystem

  override def apply() = actorSystem
}

case class ActorSystemComponent(name: String)(implicit config: Config, actorConfigs: Iterable[ActorConfig]) extends Component[ActorSystemComponentInstance] {

  override def create(): ActorSystemComponentInstance[Constructed] = {
    val actorSystem = ActorSystem(name, config)
    ActorSystemComponentInstance[Constructed](actorSystem)
  }

  override def init(comp: ActorSystemComponentInstance[Constructed]): ActorSystemComponentInstance[Initialized] = {
    def createActorRegistryActor(actorSystem: ActorSystem) = {
      val actorRegistryConfig = ActorConfig(actorClass = classOf[ActorRegistry],
        topLevelActor = true,
        actorPath = actorSystem / ActorRegistry.ACTOR_NAME)
      val actorRegistry = actorRegistryConfig.actorOfActorSystem(actorSystem)

      implicit val askTimeout = Timeout(1 second)
      val reply = actorRegistry ? Message(HeartbeatRequest)
      Await.result(reply, 1 second)
    }

    createActorRegistryActor(comp.actorSystem)
    comp.copy[Initialized]()
  }

  override def start(comp: ActorSystemComponentInstance[Initialized]): ActorSystemComponentInstance[Started] = {
    implicit val actorSystem = comp.actorSystem
    actorConfigs.foreach(ActorConfigRegistry.register(actorSystem.name, _))
    actorConfigs.filter(actorConfig => actorConfig.topLevelActor).foreach(_.actorOfActorSystem)
    comp.copy[Started]()
  }

  override def stop(comp: ActorSystemComponentInstance[Started]): ActorSystemComponentInstance[Stopped] = {
    comp.actorSystem.shutdown()
    val log = logger
    while (!comp.actorSystem.isTerminated) {
      log.info("Waiting for ActorSystem to shutdown : {}", name)
      try {
        comp.actorSystem.awaitTermination(30 second)
      } catch {
        case e: TimeoutException => // ignore
      }
    }

    comp.copy[Stopped]()
  }

}