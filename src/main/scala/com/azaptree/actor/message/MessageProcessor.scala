package com.azaptree.actor.message

import com.azaptree.actor.ConfigurableActor
import com.azaptree.actor.message.system.SystemMessage
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.UnhandledMessage
import com.azaptree.actor.message.system.ApplicationMessageSupported
import com.azaptree.actor.message.system.IsApplicationMessageSupported
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.ChildrenActorPaths
import com.azaptree.actor.message.system.MessageStats
import com.azaptree.actor.message.system.GetActorConfig
import com.azaptree.actor.message.system.GetMessageStats
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.GetChildrenActorPaths
import akka.actor.Terminated
import com.azaptree.actor.application.ActorRegistry
import com.azaptree.actor.application.ActorRegistry.RegisterActor
import akka.actor.ReceiveTimeout
import akka.event.LoggingReceive

/**
 * Handles messages that are wrapped within Message[A].
 * If a message is received that is not wrapped within a Message[A],
 * then it a new Message[A] envelope will be created for it and then processed.
 *
 * The main purpose of placing all messages within a Message[A] envelope is to track messages in a consistent manner.
 */
trait MessageProcessor extends ConfigurableActor with MessageLogging with SystemMessageProcessing {

  private[this] val processApplicationMessage = receiveMessage orElse (unsupportedMessageTypeException)

  /**
   * Sub-classes can override this method to provide the message handling logic.
   * This should handle all Messages where Message.data is not of type: SystemMessage
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */
  protected def receiveMessage: PartialFunction[Message[_], Unit]

  /**
   * Registers with the ActorRegistry
   */
  override def preStart() = {
    context.actorSelection(ActorRegistry.ACTOR_PATH) ! Message(RegisterActor(context.self))
  }

  import akka.actor.Status._
  /**
   * If it's a Message[_] and the message specifies that it is expecting a reply,
   * then a Failure() response message is returned containing an IllegalArgumentException.
   *
   * Otherwise, it records that that the message failed and logs an UnhandledMessage to the ActorSystem.eventStream
   *
   */
  protected def unhandledMessage: Receive = {
    case msg: Message[_] if msg.metadata.expectingReply =>
      sender ! Failure(new IllegalArgumentException(s"Message was not handled: $msg"))
      messageFailed()
      context.system.eventStream.publish(new UnhandledMessage(msg, sender, context.self))
    case msg =>
      messageFailed()
      context.system.eventStream.publish(new UnhandledMessage(msg, sender, context.self))
  }

  protected def handleFailure(failure: Failure): Unit = {
    log.warning("Received Failure", failure.cause)
  }

  private def unsupportedMessageTypeException: Receive = {
    case _ => throw new UnsupportedMessageTypeException()
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
  override def receive: Receive = {
    def process: Receive = {
      case msg: Message[_] =>
        val updatedMetadata = msg.metadata.copy(processingResults = ProcessingResult(senderActorPath = sender.path, actorPath = self.path) :: msg.metadata.processingResults)
        val message = msg.copy(metadata = updatedMetadata)

        message match {
          case m @ Message(sysMsg: SystemMessage, _) => processSystemMessage(message.asInstanceOf[Message[SystemMessage]])
          case Message(failure: Failure, _) => handleFailure(failure)
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
              case e: UnsupportedMessageTypeException =>
                unhandledMessage(msg)
              case e: Exception =>
                messageFailed()
                logMessage(message.update(status = unexpectedError("Failed to process message", e)))
                throw e
            }
        }
      case other => process(Message(other))
    }

    val processMessage: Receive = if (actorConfig.loggingReceive) {
      LoggingReceive { process }
    } else { process }

    processMessage
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

