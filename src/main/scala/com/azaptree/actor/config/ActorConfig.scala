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
import akka.actor.ActorPath
import scala.concurrent.duration.FiniteDuration

@SerialVersionUID(1L)
case class ActorConfig(
    actorClass: Class[_ <: Actor],
    actorPath: ActorPath,
    routedTo: Boolean = false,
    loggingReceive: Boolean = false,
    supervisorStrategy: Either[SupervisorStrategyConfig, SupervisorStrategy] = Left(SupervisorStrategyConfig()),
    topLevelActor: Boolean = false,
    // used to provide any Actor specific config
    config: Option[Config] = None,
    /*
   * If specified, then the Actor will be gracefully stopped before the ActorSystem shutdown commences.
   *
   * If not specified, then the Actor will be shutdown upon ActorSystem shutdown.
   */
    gracefulStopTimeout: Option[FiniteDuration] = None) {

  def name: String = actorPath.name

  def actorOfActorSystem(implicit actorSystem: ActorSystem): ActorRef = {
    ActorConfigRegistry.register(actorSystem.name, this)
    actorSystem.actorOf(Props(actorClass), name)
  }

  def actorOfActorContext(implicit actorContext: ActorContext): ActorRef = {
    ActorConfigRegistry.register(actorContext.system.name, this)
    actorContext.actorOf(Props(actorClass), name)
  }

  def registerIfTopLevelActor(implicit actorSystem: ActorSystem) = {
    if (topLevelActor) ActorConfigRegistry.register(actorSystem.name, this)
  }
}

@SerialVersionUID(1L)
case class SupervisorStrategyConfig(supervisorStrategyType: SupervisorStrategyType = OneForOne, maxNrOfRetries: Int = -1, withinTimeRange: Duration = Duration.Inf)

sealed trait SupervisorStrategyType

case object OneForOne extends SupervisorStrategyType

case object AllForOne extends SupervisorStrategyType

