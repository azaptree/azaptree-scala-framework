package com.azaptree.actors

import java.util.concurrent.TimeUnit

import com.azaptree.actors.message.Heartbeat

import akka.actor.ActorPath
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout

object ActorSystemManager {

  private[this] var actorSystems = Map[Symbol, ActorSystem]()

  def actorSystem(name: Symbol): Option[ActorSystem] = actorSystems.get(name)

  def actorSystemNames: Set[Symbol] = actorSystems.keySet

  def registerActorSystem(actorSystem: ActorSystem): Unit = {
    val key = Symbol(actorSystem.name)
    require(actorSystems.get(key) == None, "An ActorSystem by the same name is already registered")

    actorSystems = actorSystems + (key -> actorSystem)
  }

  def shutdownAll(): Unit = {
    actorSystems.values foreach (_.shutdown)
    actorSystems.values foreach (_.awaitTermination)
  }

  def stopActorNow(actorSystemName: Symbol, actorPath: ActorPath): Unit = {
    val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)

    import akka.pattern.ask
    ask(actor, Heartbeat)(Timeout(5, TimeUnit.SECONDS))

  }

}