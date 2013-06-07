package com.azaptree.actor.message

import com.azaptree.actor.message.system.SystemMessageProcessor
import com.azaptree.actor.message.system.ApplicationMessageSupported
import com.azaptree.actor.message.system.GetActorConfig
import com.azaptree.actor.message.system.IsApplicationMessageSupported
import com.azaptree.actor.message.system.GetSystemMessageProcessorActorRef
import akka.actor.UnhandledMessage
import com.azaptree.actor.message.system.GetMessageStats
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.ChildrenActorPaths
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.GetChildrenActorPaths
import com.azaptree.actor.message.system.MessageStats
import com.azaptree.actor.message.system.SystemMessage

trait SystemMessageProcessing {
  self: MessageProcessor =>

  val processSystemMessage = receiveSystemMessage orElse unhandledSystemMessage

  /**
   * Publishes an UnhandledMessage event to the ActorSystem.eventStream
   */
  private def unhandledSystemMessage: PartialFunction[Message[SystemMessage], Unit] = {
    case msg =>
      if (msg.metadata.expectingReply) {
        sender ! akka.actor.Status.Failure(new IllegalArgumentException(s"Message was not handled: $msg"))
      }
      context.system.eventStream.publish(new UnhandledMessage(msg, sender, context.self))
  }

  private def receiveSystemMessage: PartialFunction[Message[SystemMessage], Unit] = {
    case m @ Message(HeartbeatRequest, _) => tryProcessingSystemMessage(m, processHeartbeat)
    case m @ Message(GetMessageStats, _) => tryProcessingSystemMessage(m, processGetMessageStats)
    case m @ Message(GetActorConfig, _) => tryProcessingSystemMessage(m, processGetActorConfig)
    case m @ Message(GetChildrenActorPaths, _) => tryProcessingSystemMessage(m, processGetChildrenActorPaths)
    case m @ Message(GetSystemMessageProcessorActorRef, _) => tryProcessingSystemMessage(m, getSystemMessageProcessorActorRef)
    case m @ Message(IsApplicationMessageSupported(_), _) => tryProcessingSystemMessage(m, isApplicationMessageSupported)
  }

  private def isApplicationMessageSupported(message: Message[_]) = {
    message.data match {
      case IsApplicationMessageSupported(msg: Message[_]) =>
        sender ! Message[ApplicationMessageSupported](
          data = ApplicationMessageSupported(msg, receiveMessage.isDefinedAt(msg)),
          metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    }
  }

  private def tryProcessingSystemMessage(message: Message[_], f: Message[_] => Unit) = {
    val updatedMetadata = message.metadata.copy(processingResults = ProcessingResult(senderActorPath = sender.path, actorPath = self.path) :: message.metadata.processingResults)
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

  private def getSystemMessageProcessorActorRef(message: Message[_]) = {
    sender ! Message[SystemMessageProcessor](
      data = SystemMessageProcessor(context.self),
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   */
  private def processGetActorConfig(message: Message[_]): Unit = {
    sender ! Message[ActorConfig](
      data = actorConfig,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[ChildrenActorPaths] reply back to the sender.
   */
  private def processGetChildrenActorPaths(message: Message[_]): Unit = {
    val childActorPaths = context.children.map(_.path)
    sender ! Message[ChildrenActorPaths](
      data = ChildrenActorPaths(childActorPaths),
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   */
  private def processGetMessageStats(message: Message[_]): Unit = {
    sender ! Message[MessageStats](
      data = messageStats,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[HeartbeatResponse.type] reply back to the sender.
   */
  private def processHeartbeat(message: Message[_]): Unit = {
    heartbeatReceived()
    sender ! Message[HeartbeatResponse.type](
      data = HeartbeatResponse,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }
}