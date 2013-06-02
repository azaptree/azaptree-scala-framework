package com.azaptree.actor.message

import com.azaptree.actor.ConfigurableActor
import com.azaptree.actor.message.system.SystemMessage
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.UnhandledMessage
import com.azaptree.actor.message.system.SystemMessageProcessor
import com.azaptree.actor.message.system.ApplicationMessageSupported
import com.azaptree.actor.message.system.IsApplicationMessageSupported
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.ChildrenActorPaths
import com.azaptree.actor.message.system.MessageStats
import com.azaptree.actor.message.system.GetActorConfig
import com.azaptree.actor.message.system.GetSystemMessageProcessorActorRef
import com.azaptree.actor.message.system.GetMessageStats
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.GetChildrenActorPaths

trait MessageProcessor extends ConfigurableActor with MessageLogging {

  val processApplicationMessage = processMessage orElse (unhandledMessage andThen unsupportedMessageTypeException)

  /**
   * Sub-classes can override this method to provide the message handling logic.
   * This should handle all Message where Message.data is not of type: SystemMessage
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */
  def processMessage: PartialFunction[Message[_], Unit]

  def processSystemMessage: PartialFunction[Any, Unit] = {
    case m @ Message(HeartbeatRequest, _) => tryProcessingSystemMessage(m, processHeartbeat)
    case m @ Message(GetMessageStats, _) => tryProcessingSystemMessage(m, processGetMessageStats)
    case m @ Message(GetActorConfig, _) => tryProcessingSystemMessage(m, processGetActorConfig)
    case m @ Message(GetChildrenActorPaths, _) => tryProcessingSystemMessage(m, processGetChildrenActorPaths)
    case m @ Message(GetSystemMessageProcessorActorRef, _) => tryProcessingSystemMessage(m, getSystemMessageProcessorActorRef)
    case m @ Message(IsApplicationMessageSupported(_), _) => tryProcessingSystemMessage(m, isApplicationMessageSupported)
    case m => log.warning("Received unknown SystemMessage : {}", m)
  }

  /**
   * Records that that the message failed and logs a UnhandledMessage to the ActorSystem.eventStream
   *
   */
  def unhandledMessage: PartialFunction[Any, Unit] = {
    case msg =>
      messageFailed()
      context.system.eventStream.publish(new UnhandledMessage(msg, sender, context.self))
  }

  def unsupportedMessageTypeException: PartialFunction[Any, Unit] = {
    case _ =>
      throw new UnsupportedMessageTypeException()
  }

  /**
   * All exceptions are bubbled up to be handled by the parent SupervisorStrategy.
   *
   */
  def process(msg: Message[_]): Unit = {
    val updatedMetadata = msg.metadata.copy(processingResults = ProcessingResult(senderActorPath = sender.path, actorPath = self.path) :: msg.metadata.processingResults)
    val message = msg.copy(metadata = updatedMetadata)

    message match {
      case m @ Message(sysMsg: SystemMessage, _) => processSystemMessage(message.asInstanceOf[Message[SystemMessage]])
      case _ =>
        messageReceived()
        try {
          processApplicationMessage(message)
          messageProcessed()
          if (!message.metadata.processingResults.head.status.isDefined) {
            logMessage(message.update(status = SUCCESS_MESSAGE_STATUS))
          } else {
            logMessage(message)
          }
        } catch {
          case e: UnsupportedMessageTypeException => //ignore - this is already handled within processApplicationMessage via handleInvalidMessage
          case e: Exception =>
            messageFailed()
            logMessage(message.update(status = unexpectedError("Failed to process message", e)))
            throw e
        }
    }

  }

  def isApplicationMessageSupported(message: Message[_]) = {
    message.data match {
      case IsApplicationMessageSupported(msg: Message[_]) =>
        sender ! Message[ApplicationMessageSupported](
          data = ApplicationMessageSupported(msg, processMessage.isDefinedAt(msg)),
          metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
    }
  }

  def tryProcessingSystemMessage(message: Message[_], f: Message[_] => Unit) = {
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
      data = actorConfig,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[ChildrenActorPaths] reply back to the sender.
   */
  def processGetChildrenActorPaths(message: Message[_]): Unit = {
    val childActorPaths = context.children.map(_.path)
    sender ! Message[ChildrenActorPaths](
      data = ChildrenActorPaths(childActorPaths),
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   */
  def processGetMessageStats(message: Message[_]): Unit = {
    sender ! Message[MessageStats](
      data = messageStats,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

  /**
   * Sends a Message[HeartbeatResponse.type] reply back to the sender.
   */
  def processHeartbeat(message: Message[_]): Unit = {
    heartbeatReceived()
    sender ! Message[HeartbeatResponse.type](
      data = HeartbeatResponse,
      metadata = MessageMetadata(processingResults = message.metadata.processingResults.head.success :: message.metadata.processingResults.tail))
  }

}

@SerialVersionUID(1L)
class UnsupportedMessageTypeException() extends RuntimeException {}

/**
 * Thrown when a SystemMessage processing exception occurs.
 * This can be used by the SupervisorStrategy to identify and handle a SystemMessage processing exception accordingly.
 */
@SerialVersionUID(1L)
class SystemMessageProcessingException(cause: Throwable) extends RuntimeException(cause) {}

