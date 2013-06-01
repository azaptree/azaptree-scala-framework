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
import akka.actor.ActorContext
import com.azaptree.actor.message.system.GetChildrenActorPaths
import com.azaptree.actor.message.system.ChildrenActorPaths

trait SystemMessageProcessing {
  self: ConfigurableActor with ActorLogging with MessageLogging =>

  import SystemMessageProcessorActor._

  def createSystemMessageProcessorActor: ActorRef = {
    context.actorOf(Props(new SystemMessageProcessorActor(actorConfig, stats, context)), SYSTEM_MESSAGE_PROCESSOR_ACTOR_NAME)
  }

  def processSystemMessage(message: Message[SystemMessage]) = {
    val systemMessageProcessingActor = context.child(SYSTEM_MESSAGE_PROCESSOR_ACTOR_NAME).getOrElse(createSystemMessageProcessorActor)
    systemMessageProcessingActor.forward(message.update(status = SUCCESS_MESSAGE_STATUS))
  }
}

object SystemMessageProcessorActor {
  val SYSTEM_MESSAGE_PROCESSOR_ACTOR_NAME = "systemMessageProcessor"
}

class SystemMessageProcessorActor(actorConfig: ActorConfig, stats: MessageLoggingStats, parentContext: ActorContext) extends Actor with ActorLogging {

  override def receive = {
    case message @ Message(HeartbeatRequest, _) => tryProcessingSystemMessage(message, processHeartbeat)
    case message @ Message(GetMessageStats, _) => tryProcessingSystemMessage(message, processGetMessageStats)
    case message @ Message(GetActorConfig, _) => tryProcessingSystemMessage(message, processGetActorConfig)
    case message @ Message(GetChildrenActorPaths, _) => tryProcessingSystemMessage(message, processGetChildrenActorPaths)
    case message => log.warning("Received unknown SystemMessage : {}", message)
  }

  def tryProcessingSystemMessage(message: Message[_], f: Message[_] => Unit) = {
    val updatedMetadata = message.metadata.copy(processingResults = ProcessingResult(actorPath = self.path) :: message.metadata.processingResults)
    val messageWithUpdatedProcessingResults = message.copy(metadata = updatedMetadata)
    try {
      f(messageWithUpdatedProcessingResults)
      log.info("{}", messageWithUpdatedProcessingResults.update(status = SUCCESS_MESSAGE_STATUS))
    } catch {
      case e: SystemMessageProcessingException =>
        log.error("{}", messageWithUpdatedProcessingResults.update(status = unexpectedError("Failed to process SystemMessage", e)))
        throw e
      case e: Exception =>
        log.error("{}", messageWithUpdatedProcessingResults.update(status = unexpectedError("Failed to process SystemMessage", e)))
        throw new SystemMessageProcessingException(e)
    }
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   */
  def processGetActorConfig(message: Message[_]): Unit = {
    val response = Message[ActorConfig](
      data = actorConfig,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

  /**
   * Sends a Message[ChildrenActorPaths] reply back to the sender.
   */
  def processGetChildrenActorPaths(message: Message[_]): Unit = {
    val childActorPaths = parentContext.children.map(_.path)
    val response = Message[ChildrenActorPaths](
      data = ChildrenActorPaths(childActorPaths),
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   */
  def processGetMessageStats(message: Message[_]): Unit = {
    val response = Message[MessageStats](
      data = stats.messageStats,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

  /**
   * Sends a Message[HeartbeatResponse.type] reply back to the sender.
   */
  def processHeartbeat(message: Message[_]): Unit = {
    stats.lastHeartbeatOn = System.currentTimeMillis
    val response = Message[HeartbeatResponse.type](
      data = HeartbeatResponse,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    sender ! response
  }

}