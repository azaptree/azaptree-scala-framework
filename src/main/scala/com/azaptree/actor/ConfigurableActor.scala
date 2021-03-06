package com.azaptree.actor

import akka.actor.Actor
import akka.actor.ActorRef
import com.azaptree.actor.config.ActorConfig
import akka.routing.NoRouter
import akka.actor.actorRef2Scala
import com.azaptree.actor.config.ActorConfigRegistry
import akka.actor.ActorLogging
import akka.actor.SupervisorStrategy
import akka.actor.OneForOneStrategy
import akka.actor.AllForOneStrategy

trait ConfigurableActor extends Actor with ActorLogging {
  self =>

  def actorConfig: ActorConfig = { ActorConfigRegistry.getActorConfig(context.system.name, context.self.path).getOrElse(ActorConfig(actorClass = self.getClass(), actorPath = context.self.path)) }

  override val supervisorStrategy = {
    import com.azaptree.actor.config._

    actorConfig.supervisorStrategy match {
      case Left(supervisorStrategyConfig) =>
        supervisorStrategyConfig.supervisorStrategyType match {
          case OneForOne =>
            OneForOneStrategy(supervisorStrategyConfig.maxNrOfRetries, supervisorStrategyConfig.withinTimeRange) {
              unsupportedMessageTypeExceptionDecider orElse supervisorStrategyDecider
            }
          case AllForOne =>
            AllForOneStrategy(supervisorStrategyConfig.maxNrOfRetries, supervisorStrategyConfig.withinTimeRange) {
              unsupportedMessageTypeExceptionDecider orElse supervisorStrategyDecider
            }
        }
      case Right(strategy) => strategy
    }
  }

  def supervisorStrategyDecider = SupervisorStrategy.defaultStrategy.decider

  /**
   * Used to send messages to other Actors.
   *
   * If routed to, then the sender will be the parent, i.e., the head router.
   */
  val tell: (ActorRef, Any) => Unit =
    if (actorConfig.routedTo) {
      (actorRef: ActorRef, msg: Any) => actorRef.tell(msg, context.parent)
    } else {
      (actorRef: ActorRef, msg: Any) => actorRef ! msg
    }

}