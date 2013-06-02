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
   * For Actors that require custom ActorConfigs, they must be registered before the Actors are created.
   * ActorConfigs are registered per ActorSystem
   *
   */
  object ActorConfigRegistry {
    private[this] var actorConfigs: Map[String, Map[ActorPath, ActorConfig]] = Map[String, Map[ActorPath, ActorConfig]]()

    def getActorConfig(actorSystemName: String, actorPath: ActorPath): Option[ActorConfig] = {
      actorConfigs.get(actorSystemName).flatMap(_.get(actorPath))
    }

    def actorSystemNames = {
      actorConfigs.keySet
    }

    def actorPaths(actorSystemName: String) = {
      actorConfigs.keySet
    }

    def register(actorSystemName: String, actorPath: ActorPath, actorConfig: ActorConfig) = {
      var actorSystemActorConfigs = actorConfigs.get(actorSystemName).getOrElse(Map[ActorPath, ActorConfig]())
      actorSystemActorConfigs = actorSystemActorConfigs + (actorPath -> actorConfig)
      actorConfigs = actorConfigs + (actorSystemName -> actorSystemActorConfigs)
    }

  }

}