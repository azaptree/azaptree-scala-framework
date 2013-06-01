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

  def processSystemMessage(implicit message: Message[SystemMessage]) = {
    try {
      message.data match {
        case HeartbeatRequest => processHeartbeat
        case GetMessageStats => processGetMessageStats
        case GetActorConfig => processGetActorConfig
        case _ => log.warning("Received unknown SystemMessage : {}", message)
      }
      log.debug("{}", message.update(status = SUCCESS_MESSAGE_STATUS))
    } catch {
      case e: SystemMessageProcessingException =>
        log.error("{}", message.update(status = unexpectedError("Failed to process SystemMessage", e)))
        throw e
      case e: Exception =>
        log.error("{}", message.update(status = unexpectedError("Failed to process SystemMessage", e)))
        throw new SystemMessageProcessingException(e)
    }
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   * The response message gets logged
   */
  def processGetActorConfig(implicit message: Message[_]): Unit = {
    val response = Message[ActorConfig](
      data = actorConfig,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   * The response message gets logged
   */
  def processGetMessageStats(implicit message: Message[_]): Unit = {
    val response = Message[MessageStats](
      data = messageStats,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

  /**
   * Sends a Message[HeartbeatResponse.type] reply back to the sender.
   * The response message gets logged.
   */
  def processHeartbeat(implicit message: Message[_]): Unit = {
    stats.lastHeartbeatOn = System.currentTimeMillis
    val response = Message[HeartbeatResponse.type](
      data = HeartbeatResponse,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

}