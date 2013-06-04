package com.azaptree.actor

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import akka.actor.ActorPath
import akka.actor.ActorSystem
import akka.actor.Kill
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import akka.pattern.AskTimeoutException
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import com.azaptree.actor.message.system._
import com.azaptree.actor.message.Message
import com.typesafe.config.ConfigObject

object ActorSystemManager {

  private[this] var actorSystems = Map[String, ActorSystem]()

  def actorSystem(name: String): Option[ActorSystem] = actorSystems.get(name)

  def actorSystemNames: Set[String] = actorSystems.keySet

  def registerActorSystem(actorSystem: ActorSystem): Unit = {
    require(actorSystems.get(actorSystem.name) == None, "An ActorSystem by the same name is already registered")
    actorSystems = actorSystems + (actorSystem.name -> actorSystem)
  }

  def shutdownAll(): Unit = {
    actorSystems.values foreach (_.shutdown)
    actorSystems.values foreach (_.awaitTermination)
  }

  /**
   * Any messages in the Actor's mailbox will be discarded
   */
  def stopActorNow(actorSystemName: String, actorPath: ActorPath): Unit = {
    val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)
    actorSystem.stop(actor)
  }

  /**
   * Sends a PoisonPill message to the Actor, which will stop the Actor when the message is processed.
   * PoisonPill is enqueued as ordinary messages and will be handled after messages that were already queued in the mailbox
   */
  def stopActor(actorSystemName: String, actorPath: ActorPath): Unit = {
    val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)
    actor ! PoisonPill
  }

  def gracefulStop(actorSystemName: String, actorPath: ActorPath, timeout: FiniteDuration = 1 second): Unit = {
    import scala.concurrent.duration._
    implicit val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)
    try {
      val stopped: Future[Boolean] = akka.pattern.gracefulStop(actor, timeout)
      Await.result(stopped, timeout)
    } catch {
      case e: AskTimeoutException =>
    }
  }

  def restartActor(actorSystemName: String, actorPath: ActorPath): Unit = {
    val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)
    actor ! Kill
  }

  def sendHeartbeat(actorSystemName: String, actorPath: ActorPath, timeout: FiniteDuration = 1 second): Option[Message[HeartbeatResponse.type]] = {
    val actorSystem = actorSystems(actorSystemName)
    val actor = actorSystem.actorFor(actorPath)

    import scala.concurrent.duration._
    import akka.pattern.ask

    try {
      val response = actor.ask(HeartbeatRequest)(timeout).mapTo[Message[HeartbeatResponse.type]]
      Some(Await.result(response, timeout))
    } catch {
      case e: AskTimeoutException => None
    }
  }

  def getActorSystemConfig(actorSystemName: String): Option[ConfigObject] = {
    actorSystem(actorSystemName).map(_.settings.config.root())
  }

}