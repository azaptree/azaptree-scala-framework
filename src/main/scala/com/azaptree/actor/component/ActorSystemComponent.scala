package com.azaptree.actor.component

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import org.slf4j.LoggerFactory

import com.azaptree.actor.application.ActorRegistry
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.config.ActorConfigRegistry
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.application.Component
import com.azaptree.application.ComponentConstructed
import com.azaptree.application.ComponentInitialized
import com.azaptree.application.ComponentLifeCycle
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.application.ComponentStarted
import com.azaptree.application.ComponentStopped
import com.typesafe.config.Config

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout

case class ActorSystemComponentLifeCycle(implicit config: Config, createActorConfigs: ActorSystem => Iterable[ActorConfig]) extends ComponentLifeCycle[ActorSystem] {

  override def create(comp: Component[ComponentNotConstructed, ActorSystem]): Component[ComponentConstructed, ActorSystem] = {
    val actorSystem = ActorSystem(comp.name, config)
    comp.copy[ComponentConstructed, ActorSystem](componentObject = Some(actorSystem))
  }

  override def init(comp: Component[ComponentConstructed, ActorSystem]): Component[ComponentInitialized, ActorSystem] = {
    def createActorRegistryActor(actorSystem: ActorSystem) = {
      val actorRegistryConfig = ActorConfig(actorClass = classOf[ActorRegistry],
        topLevelActor = true,
        actorPath = actorSystem / ActorRegistry.ACTOR_NAME)
      val actorRegistry = actorRegistryConfig.actorOfActorSystem(actorSystem)

      implicit val askTimeout = Timeout(1 second)
      val reply = actorRegistry ? Message(HeartbeatRequest)
      Await.result(reply, 1 second)
    }

    createActorRegistryActor(comp.componentObject.get)
    comp.copy[ComponentInitialized, ActorSystem]()
  }

  override def start(comp: Component[ComponentInitialized, ActorSystem]): Component[ComponentStarted, ActorSystem] = {
    implicit val actorSystem = comp.componentObject.get
    val actorConfigs = createActorConfigs(actorSystem)
    actorConfigs.foreach(ActorConfigRegistry.register(actorSystem.name, _))
    actorConfigs.filter(actorConfig => actorConfig.topLevelActor).foreach(_.actorOfActorSystem)
    comp.copy[ComponentStarted, ActorSystem]()
  }

  override def stop(comp: Component[ComponentStarted, ActorSystem]): Component[ComponentStopped, ActorSystem] = {
    val actorSystem = comp.componentObject.get
    actorSystem.shutdown()
    val logger = LoggerFactory.getLogger(getClass())
    while (!actorSystem.isTerminated) {
      logger.info("Waiting for ActorSystem to shutdown : {}", comp.name)
      try {
        actorSystem.awaitTermination(30 second)
      } catch {
        case e: TimeoutException => // ignore
      }
    }

    comp.copy[ComponentStopped, ActorSystem](componentObject = None)
  }

}
