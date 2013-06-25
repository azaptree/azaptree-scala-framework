package com.azaptree.actor

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import com.azaptree.actor.message.Message
import com.azaptree.actor.message.system.HeartbeatRequest
import com.typesafe.config.ConfigObject
import akka.actor.ActorPath
import akka.actor.ActorSelection.toScala
import akka.actor.ActorSystem
import akka.actor.Kill
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import akka.pattern.AskTimeoutException
import akka.pattern.ask
import akka.util.Timeout.durationToTimeout
import com.azaptree.actor.message.system.HeartbeatResponse
import org.slf4j.LoggerFactory
import akka.actor.ActorRef

object ActorSystemManager {

  def shutdown(actorSystem: ActorSystem, waitTimeout: Duration = Duration.Inf): Unit = {
    actorSystem.shutdown
    actorSystem.awaitTermination(waitTimeout)
  }

  /**
   * Any messages in the Actor's mailbox will be discarded
   */
  def stopActorNow(actorSystem: ActorSystem, actorPath: ActorPath): Unit = {
    val actor = actorSystem.actorSelection(actorPath)
    actor ! Kill
  }

  /**
   * Sends a PoisonPill message to the Actor, which will stop the Actor when the message is processed.
   * PoisonPill is enqueued as ordinary messages and will be handled after messages that were already queued in the mailbox
   */
  def stopActor(actorSystem: ActorSystem, actorPath: ActorPath): Unit = {
    val actor = actorSystem.actorSelection(actorPath)
    actor ! PoisonPill
  }

  def gracefulStop(actor: ActorRef, timeout: FiniteDuration = 1 second): Unit = {
    import scala.concurrent.duration._
    try {
      val stopped: Future[Boolean] = akka.pattern.gracefulStop(actor, timeout)
      Await.result(stopped, (timeout plus (1 second)))
    } catch {
      case e: AskTimeoutException =>
        val log = LoggerFactory.getLogger("com.azaptree.actor.ActorSystemManager")
        val msgArgs = Array(actor.path, timeout)
        log.warn("gracefulStop() timed out for : {} : timeout = {}", msgArgs: _*)
    }
  }

  def restartActor(actorSystem: ActorSystem, actorPath: ActorPath): Unit = {
    actorSystem.actorSelection(actorPath.toString()) ! Kill
  }

  def sendHeartbeat(actorSystem: ActorSystem, actorPath: ActorPath, timeout: FiniteDuration = 1 second): Option[Message[HeartbeatResponse.type]] = {
    val actor = actorSystem.actorSelection(actorPath)

    import scala.concurrent.duration._
    import akka.pattern.ask

    try {
      val response = actor.ask(HeartbeatRequest)(timeout).mapTo[Message[HeartbeatResponse.type]]
      Some(Await.result(response, timeout))
    } catch {
      case e: AskTimeoutException => None
    }
  }

  def getActorSystemConfig(actorSystem: ActorSystem): ConfigObject = {
    actorSystem.settings.config.root()
  }

}