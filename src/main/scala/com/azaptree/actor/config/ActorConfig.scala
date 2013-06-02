package com.azaptree.actor.config

import akka.actor.Address
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.actor.ActorPath
import com.typesafe.config.Config
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.ActorContext

@SerialVersionUID(1L)
case class ActorConfig(
    actorClass: Class[_ <: Actor],
    name: String,
    routedTo: Boolean = false,
    loggingReceive: Boolean = false,
    supervisorStrategy: Option[SupervisorStrategy] = Some(DEFAULT_SUPERVISOR_STRATEGY),
    topLevelActor: Boolean = false,
    // used to provide any Actor specific config
    config: Option[Config] = None) {

  def actorOfActorSystem(implicit actorSystem: ActorSystem): ActorRef = {
    ActorConfigRegistry.register(actorSystem.name, actorSystem / name, this)
    actorSystem.actorOf(Props(actorClass), name)
  }

  def actorOfActorContext(implicit actorContext: ActorContext): ActorRef = {
    ActorConfigRegistry.register(actorContext.system.name, actorContext.self.path / name, this)
    actorContext.actorOf(Props(actorClass), name)
  }

  def registerIfTopLevelActor(implicit actorSystem: ActorSystem) = {
    if (topLevelActor) ActorConfigRegistry.register(actorSystem.name, actorSystem / name, this)
  }
}

