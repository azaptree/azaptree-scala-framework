package com.azaptree.actor.config

import scala.concurrent.duration.Duration
import com.typesafe.config.Config
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.actor.OneForOneStrategy
import akka.actor.Actor

import scala.language.existentials

@SerialVersionUID(1L)
case class ActorConfig(
    actorClass: Class[_ <: Actor],
    name: String,
    routedTo: Boolean = false,
    loggingReceive: Boolean = false,
    supervisorStrategy: Either[SupervisorStrategyConfig, SupervisorStrategy] = Right(new OneForOneStrategy()(
      unsupportedMessageTypeExceptionDecider orElse SupervisorStrategy.defaultStrategy.decider
    )),
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

@SerialVersionUID(1L)
case class SupervisorStrategyConfig(supervisorStrategyType: SupervisorStrategyType = OneForOne, maxNrOfRetries: Int = -1, withinTimeRange: Duration = Duration.Inf)

sealed trait SupervisorStrategyType

case object OneForOne extends SupervisorStrategyType

case object AllForOne extends SupervisorStrategyType

