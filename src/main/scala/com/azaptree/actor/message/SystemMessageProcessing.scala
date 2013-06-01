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
import akka.actor.ActorRef
import akka.actor.Props

trait SystemMessageProcessing {
  self: ConfigurableActor with ActorLogging with MessageLogging =>

  private[this] val SYSTEM_MESSAGE_PROCESSOR_ACTOR_NAME = "systemMessageProcessor"

  def createSystemMessageProcessorActor: ActorRef = {
    context.actorOf(Props(new SystemMessageProcessorActor(actorConfig, stats)), SYSTEM_MESSAGE_PROCESSOR_ACTOR_NAME)
  }

  def processSystemMessage(message: Message[SystemMessage]) = {
    val systemMessageProcessingActor = context.child(SYSTEM_MESSAGE_PROCESSOR_ACTOR_NAME).getOrElse(createSystemMessageProcessorActor)
    systemMessageProcessingActor.forward(message)
  }
}

class SystemMessageProcessorActor(actorConfig: ActorConfig, stats: MessageLoggingStats) extends Actor with ActorLogging {

  override def receive = {
    case message @ Message(HeartbeatRequest, _) => tryProcessingSystemMessage(message, processHeartbeat)
    case message @ Message(GetMessageStats, _) => tryProcessingSystemMessage(message, processGetMessageStats)
    case message @ Message(GetActorConfig, _) => tryProcessingSystemMessage(message, processGetActorConfig)
    case message => log.warning("Received unknown SystemMessage : {}", message)
  }

  def tryProcessingSystemMessage(message: Message[_], f: Message[_] => Unit) = {
    try {
      f(message)
      log.info("{}", message.update(status = SUCCESS_MESSAGE_STATUS))
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
  def processGetActorConfig(message: Message[_]): Unit = {
    val response = Message[ActorConfig](
      data = actorConfig,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   * The response message gets logged
   */
  def processGetMessageStats(message: Message[_]): Unit = {
    val response = Message[MessageStats](
      data = stats.messageStats,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

  /**
   * Sends a Message[HeartbeatResponse.type] reply back to the sender.
   * The response message gets logged.
   */
  def processHeartbeat(message: Message[_]): Unit = {
    stats.lastHeartbeatOn = System.currentTimeMillis
    val response = Message[HeartbeatResponse.type](
      data = HeartbeatResponse,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

}