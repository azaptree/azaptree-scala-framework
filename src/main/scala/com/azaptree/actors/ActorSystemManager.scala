package com.azaptree.actors

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import com.azaptree.actors.message.Heartbeat
import com.azaptree.actors.message.Message

import akka.actor.ActorPath
import akka.actor.ActorSystem
import akka.actor.Kill
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import akka.pattern.AskTimeoutException
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import com.azaptree.actors.message.Heartbeat

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

  /**
   * Any messages in the Actor's mailbox will be discarded
   */
  def stopActorNow(actorSystemName: Symbol, actorPath: ActorPath): Unit = {
    val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)
    actorSystem.stop(actor)
  }

  /**
   * Sends a PoisonPill message to the Actor, which will stop the Actor when the message is processed.
   * PoisonPill is enqueued as ordinary messages and will be handled after messages that were already queued in the mailbox
   */
  def stopActor(actorSystemName: Symbol, actorPath: ActorPath): Unit = {
    val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)
    actor ! PoisonPill
  }

  def gracefulStop(actorSystemName: Symbol, actorPath: ActorPath, timeout: FiniteDuration): Unit = {
    import scala.concurrent.duration._
    implicit val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)
    try {
      val stopped: Future[Boolean] = akka.pattern.gracefulStop(actor, timeout)
      Await.result(stopped, timeout + 1.second)
    } catch {
      case e: AskTimeoutException =>
    }
  }

  def restartActor(actorSystemName: Symbol, actorPath: ActorPath): Unit = {
    val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)
    actor ! Kill
  }

  def sendHeartbeat(actorSystemName: Symbol, actorPath: ActorPath, timeout: FiniteDuration): Option[Message[Heartbeat.type]] = {
    val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)

    import scala.concurrent.duration._
    import akka.pattern.ask
    try {
      val response = actor.ask(Heartbeat)(timeout).mapTo[Message[Heartbeat.type]]
      Some(Await.result(response, timeout + 1.second))
    } catch {
      case e: AskTimeoutException => None
    }
  }

}