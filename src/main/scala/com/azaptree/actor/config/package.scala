package com.azaptree.actor

import akka.actor.OneForOneStrategy
import scala.concurrent.duration.Duration
import com.azaptree.actor.message.UnsupportedMessageTypeException
import akka.actor.SupervisorStrategy._
import akka.actor.SupervisorStrategy
import akka.actor.ActorPath

package object config {

  val unsupportedMessageTypeExceptionDecider: PartialFunction[Throwable, Directive] = { case e: UnsupportedMessageTypeException => Resume }

  val DEFAULT_SUPERVISOR_STRATEGY = OneForOneStrategy(maxNrOfRetries = Int.MaxValue, withinTimeRange = Duration.Inf) {
    unsupportedMessageTypeExceptionDecider orElse SupervisorStrategy.defaultStrategy.decider
  }

  /**
   * For Actors that require custom ActorConfigs, they must be registered before the Actors are created  
   * 
   */
  object ActorConfigRegistry {
    private[this] var actorConfigs: Map[ActorPath, ActorConfig] = Map[ActorPath, ActorConfig]()

    def getActorConfig(actorPath: ActorPath): Option[ActorConfig] = {
      actorConfigs.get(actorPath)
    }

    def actorPaths = { actorConfigs.keySet }

    def register(actorPath: ActorPath, actorConfig: ActorConfig) = {
      actorConfigs = actorConfigs + (actorPath -> actorConfig)
    }

  }

}