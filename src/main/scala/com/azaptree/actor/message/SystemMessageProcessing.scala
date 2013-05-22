package com.azaptree.actor.message

import akka.actor.Actor
import com.azaptree.actor.message.system.GetMessageStats
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.MessageStats
import com.azaptree.actor.message.system.SystemMessage
import akka.actor.ActorLogging
import com.azaptree.actor.message.system.MessageProcessedEvent
import com.azaptree.actor.ConfigurableActor
import com.azaptree.actor.message.system.GetActorConfig
import com.azaptree.actor.config.ActorConfig

trait SystemMessageProcessing {
  self: ConfigurableActor with ActorLogging with MessageLogging =>

  def processSystemMessage(sysMsg: SystemMessage)(implicit message: Message[_]): Unit = {
    sysMsg match {
      case HeartbeatRequest =>
        processHeartbeat
      case GetMessageStats =>
        processGetMessageStats
      case GetActorConfig =>
        processGetActorConfig
      case _ => log.warning("Received unknown SystemMessage : {}", sysMsg)
    }
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   * The response message gets logged
   */
  def processGetActorConfig(implicit message: Message[_]): Unit = {
    val response = Message[ActorConfig](
      data = actorConfig,
      processingResults = message.processingResults.head.success :: message.processingResults.tail)
    sender ! response
    logMessage(response)
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   * The response message gets logged
   */
  def processGetMessageStats(implicit message: Message[_]): Unit = {
    val response = Message[MessageStats](
      data = messageStats,
      processingResults = message.processingResults.head.success :: message.processingResults.tail)
    sender ! response
    logMessage(response)
  }

  /**
   * Sends a Message[HeartbeatResponse.type] reply back to the sender.
   * The response message gets logged.
   */
  def processHeartbeat(implicit message: Message[_]): Unit = {
    lastHeartbeatOn = System.currentTimeMillis
    val response = Message[HeartbeatResponse.type](
      data = HeartbeatResponse,
      processingResults = message.processingResults.head.success :: message.processingResults.tail)
    sender ! response
    logMessage(response)
  }

}