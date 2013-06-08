package com.azaptree.actor.component

import java.util.concurrent.TimeoutException
import scala.concurrent.Await
import scala.concurrent.duration._
import com.azaptree.actor.application.ActorRegistry
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.config.ActorConfigRegistry
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.application.Component
import com.azaptree.application.ComponentInstance
import com.azaptree.application.lifecycle._
import com.typesafe.config.Config
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern._
import akka.util.Timeout
import scala.collection.immutable.TreeSet
import akka.actor.ActorPath

case class ActorSystemComponentInstance[S <: State](actorSystem: ActorSystem) extends ComponentInstance[S] {
  type A = ActorSystem

  override def apply() = actorSystem

  /**
   * Only valid to call after the component has been initialized
   */
  def actorRegistryActor: ActorRef = actorSystem.actorFor(actorRegistryActorPath)

  def actorRegistryActorPath: ActorPath = actorSystem / ActorRegistry.ACTOR_NAME

}

case class ActorSystemComponent(name: String)(implicit config: Config, createActorConfigs: ActorSystem => Iterable[ActorConfig]) extends Component[ActorSystemComponentInstance] {

  override protected def create(): ActorSystemComponentInstance[Constructed] = {
    val actorSystem = ActorSystem(name, config)
    ActorSystemComponentInstance[Constructed](actorSystem)
  }

  override protected def init(comp: ActorSystemComponentInstance[Constructed]): ActorSystemComponentInstance[Initialized] = {
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

  override protected def start(comp: ActorSystemComponentInstance[Initialized]): ActorSystemComponentInstance[Started] = {
    implicit val actorSystem = comp.actorSystem
    val actorConfigs = createActorConfigs(actorSystem)
    actorConfigs.foreach(ActorConfigRegistry.register(actorSystem.name, _))
    actorConfigs.filter(actorConfig => actorConfig.topLevelActor).foreach(_.actorOfActorSystem)
    comp.copy[Started]()
  }

  override protected def stop(comp: ActorSystemComponentInstance[Started]): ActorSystemComponentInstance[Stopped] = {
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