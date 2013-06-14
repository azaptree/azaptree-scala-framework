package com.azaptree.actor

import akka.actor.OneForOneStrategy
import scala.concurrent.duration.Duration
import com.azaptree.actor.message.UnsupportedMessageTypeException
import akka.actor.SupervisorStrategy._
import akka.actor.SupervisorStrategy
import akka.actor.ActorPath
import scala.collection.immutable.SortedMap
import scala.collection.immutable.TreeMap
import akka.actor.ActorSystem
import scala.concurrent.Lock

package object config {

  /**
   * for UnsupportedMessageTypeException, simply Resume actor processing
   */
  val unsupportedMessageTypeExceptionDecider: PartialFunction[Throwable, Directive] = { case e: UnsupportedMessageTypeException => Resume }

  /**
   * For Actors that require custom ActorConfigs, they must be registered before the Actors are created.
   * ActorConfigs are registered per ActorSystem
   *
   */
  object ActorConfigRegistry {
    @volatile
    private[this] var actorConfigs: Map[String, Map[ActorPath, ActorConfig]] = Map[String, SortedMap[ActorPath, ActorConfig]]()

    private[this] val lock = new Lock()

    def getActorConfig(actorSystemName: String, actorPath: ActorPath): Option[ActorConfig] = {
      actorConfigs.get(actorSystemName).flatMap(_.get(actorPath))
    }

    def actorSystemNames: Set[String] = {
      actorConfigs.keySet
    }

    def actorPaths(actorSystemName: String): Option[Set[ActorPath]] = {
      for (
        actorSystemActorConfigs <- actorConfigs.get(actorSystemName)
      ) yield (actorSystemActorConfigs.keySet)
    }

    def register(actorSystemName: String, actorConfig: ActorConfig) = {
      lock.acquire()
      try {
        var actorSystemActorConfigs = actorConfigs.get(actorSystemName).getOrElse(TreeMap[ActorPath, ActorConfig]())
        actorSystemActorConfigs += (actorConfig.actorPath -> actorConfig)
        actorConfigs += (actorSystemName -> actorSystemActorConfigs)
      } finally {
        lock.release()
      }
    }

    def createTopLevelActors(implicit actorSystem: ActorSystem) = {
      val actorSystemActorConfigs = actorConfigs.get(actorSystem.name)
      require(actorSystemActorConfigs.isDefined, "There are no ActorConfigs registered for ActorSystem : %s".format(actorSystem.name))
      actorSystemActorConfigs.foreach {
        _.values.filter(_.topLevelActor).foreach(_.actorOfActorSystem)
      }
    }

  }

}