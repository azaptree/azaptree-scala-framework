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
import akka.actor.Terminated
import com.azaptree.actor.application.ActorRegistry
import com.azaptree.actor.application.ActorRegistry.RegisterActor
import akka.actor.ReceiveTimeout
import akka.event.LoggingReceive

trait MessageProcessor extends ConfigurableActor with MessageLogging {

  private[this] val processApplicationMessage = receiveMessage orElse (unhandledMessage andThen unsupportedMessageTypeException)

  protected val processSystemMessage = receiveSystemMessage orElse unhandledSystemMessage

  /**
   * Sub-classes can override this method to provide the message handling logic.
   * This should handle all Message where Message.data is not of type: SystemMessage
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */
  protected def receiveMessage: PartialFunction[Message[_], Unit]

  override def preStart() = {
    context.actorSelection(ActorRegistry.ACTOR_PATH) ! Message(RegisterActor(context.self))
  }

  /**
   * Wraps the following unhandledMessages within a Message and retry to process it once more:
   * <ul>
   * <li>akka.actor.Terminated
   * </ul>
   *
   * Otherwise, it records that that the message failed and logs an UnhandledMessage to the ActorSystem.eventStream
   *
   */
  protected def unhandledMessage: Receive = {
    case t: Terminated => process(Message(t))
    case msg: Message[_] if msg.metadata.expectingReply =>
      sender ! akka.actor.Status.Failure(new IllegalArgumentException(s"Message was not handled: $msg"))
      messageFailed()
      context.system.eventStream.publish(new UnhandledMessage(msg, sender, context.self))
    case msg =>
      messageFailed()
      context.system.eventStream.publish(new UnhandledMessage(msg, sender, context.self))
  }

  private def unsupportedMessageTypeException: Receive = {
    case _ =>
      throw new UnsupportedMessageTypeException()
  }

  /**
   * invoked if a akka.actor.ReceiveTimeout message is received
   */
  def receiveTimeout(): Unit = {
  }

  /**
   *
   * If actorConfig.loggingReceive = true, then the receive is wrapped in a akka.event.LoggingReceive which then logs message invocations.
   * This is enabled by a setting in the Configuration : akka.actor.debug.receive = on
   * *** NOTE: enabling it uniformly on all actors is not usually what you need, and it would lead to endless loops if it were applied to EventHandler listeners.
   *
   * All exceptions are bubbled up to be handled by the parent SupervisorStrategy.
   *
   * <ul>Keeps track of the following metrics:
   * <li> number of successfully processed messages
   * <li> number of unsuccessfully processed messages
   * <li> last time a message was processed successfully
   * <li> last time a message was processed unsuccessfully
   * </ul>
   *
   */
  def process: Receive = {
    def receive: Receive = {
      case msg: Message[_] =>
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

    val processMessage: Receive = if (actorConfig.loggingReceive) {
      LoggingReceive { receive }
    } else { receive }

    val handleReceiveTimeout: PartialFunction[Any, Unit] = {
      case ReceiveTimeout => receiveTimeout()
    }

    processMessage orElse handleReceiveTimeout orElse unhandledMessage
  }

  /**
   * Publishes an UnhandledMessage event to the ActorSystem.eventStream
   */
  protected def unhandledSystemMessage: PartialFunction[Message[SystemMessage], Unit] = {
    case msg =>
      if (msg.metadata.expectingReply) {
        sender ! akka.actor.Status.Failure(new IllegalArgumentException(s"Message was not handled: $msg"))
      }
      context.system.eventStream.publish(new UnhandledMessage(msg, sender, context.self))
  }

  protected def receiveSystemMessage: PartialFunction[Message[SystemMessage], Unit] = {
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

@SerialVersionUID(1L)
class UnsupportedMessageTypeException() extends RuntimeException {
}

/**
 * Thrown when a SystemMessage processing exception occurs.
 * This can be used by the SupervisorStrategy to identify and handle a SystemMessage processing exception accordingly.
 */
@SerialVersionUID(1L)
class SystemMessageProcessingException(cause: Throwable) extends RuntimeException(cause) {}

