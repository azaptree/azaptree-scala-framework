package com.azaptree.actor.message

import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.message.system.ApplicationMessageSupported
import com.azaptree.actor.message.system.ChildrenActorPaths
import com.azaptree.actor.message.system.GetActorConfig
import com.azaptree.actor.message.system.GetChildrenActorPaths
import com.azaptree.actor.message.system.GetMessageStats
import com.azaptree.actor.message.system.GetSystemMessageProcessorActorRef
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.IsApplicationMessageSupported
import com.azaptree.actor.message.system.MessageStats
import com.azaptree.actor.message.system.SystemMessageProcessor

import akka.actor.Actor
import akka.actor.ActorContext
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala
import com.azaptree.actor.message.system.HeartbeatResponse

object SystemMessageProcessorActor {
  val SYSTEM_MESSAGE_PROCESSOR_ACTOR_NAME = "systemMessageProcessor"
}

class SystemMessageProcessorActor(
  messageProcessorContext: ActorContext,
  messageProcessor: MessageProcessor)
    extends Actor with ActorLogging {

  override def receive = {
    case message @ Message(HeartbeatRequest, _) => tryProcessingSystemMessage(message, processHeartbeat)
    case message @ Message(GetMessageStats, _) => tryProcessingSystemMessage(message, processGetMessageStats)
    case message @ Message(GetActorConfig, _) => tryProcessingSystemMessage(message, processGetActorConfig)
    case message @ Message(GetChildrenActorPaths, _) => tryProcessingSystemMessage(message, processGetChildrenActorPaths)
    case message @ Message(GetSystemMessageProcessorActorRef, _) => tryProcessingSystemMessage(message, getSystemMessageProcessorActorRef)
    case message @ Message(IsApplicationMessageSupported(_), _) => tryProcessingSystemMessage(message, isApplicationMessageSupported)
    case message => log.warning("Received unknown SystemMessage : {}", message)
  }

  def isApplicationMessageSupported(message: Message[_]) = {
    message.data match {
      case IsApplicationMessageSupported(msg: Message[_]) =>
        sender ! Message[ApplicationMessageSupported](
          data = ApplicationMessageSupported(msg, messageProcessor.processMessage.isDefinedAt(msg)),
          metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    }
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

  def getSystemMessageProcessorActorRef(message: Message[_]) = {
    sender ! Message[SystemMessageProcessor](
      data = SystemMessageProcessor(context.self),
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   */
  def processGetActorConfig(message: Message[_]): Unit = {
    sender ! Message[ActorConfig](
      data = messageProcessor.actorConfig,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[ChildrenActorPaths] reply back to the sender.
   */
  def processGetChildrenActorPaths(message: Message[_]): Unit = {
    val childActorPaths = messageProcessorContext.children.map(_.path)
    sender ! Message[ChildrenActorPaths](
      data = ChildrenActorPaths(childActorPaths),
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   */
  def processGetMessageStats(message: Message[_]): Unit = {
    sender ! Message[MessageStats](
      data = messageProcessor.messageStats,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[HeartbeatResponse.type] reply back to the sender.
   */
  def processHeartbeat(message: Message[_]): Unit = {
    messageProcessor.heartbeatReceived
    sender ! Message[HeartbeatResponse.type](
      data = HeartbeatResponse,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

}

/**
 * Thrown when a SystemMessage processing exception occurs.
 * This can be used by the SupervisorStrategy to identify and handle a SystemMessage processing exception accordingly.
 */
@SerialVersionUID(1L)
class SystemMessageProcessingException(cause: Throwable) extends RuntimeException(cause) {}