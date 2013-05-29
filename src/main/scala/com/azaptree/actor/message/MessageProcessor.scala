package com.azaptree.actor.message

import com.azaptree.actor.ConfigurableActor
import com.azaptree.actor.message.system.SystemMessage
import akka.actor.Actor
import akka.actor.ActorLogging

trait MessageProcessor {
  selfActor: ConfigurableActor with SystemMessageProcessing with MessageLogging with ActorLogging =>

  /**
   * Sub-classes can override this method to provide the message handling logic.
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */

  def processMessage: PartialFunction[Message[_], Unit]

  /**
   * default implementation is to throw an UnsupportedMessageTypeException
   */
  def handleUnsupportedMessageType: PartialFunction[Message[_], Unit] = {
    case msg => throw new UnsupportedMessageTypeException(msg)
  }

  val processApplicationMessage = processMessage orElse handleUnsupportedMessageType

  /**
   * All exceptions are bubbled up to be handled by the SupervisorStrategy.
   * Exceptions that occur while processing SystemMessages will be wrapped in a SystemMessageProcessingException,
   * to enable detection and separate error handling for system message processing failures.
   */
  def process(msg: Message[_]): Unit = {

    def handleMessage(message: Message[_]) = {
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
        case e: Exception =>
          messageFailed()
          logMessage(message.update(status = ERROR_MESSAGE_STATUS))
          throw e
      }
    }

    val updatedMetadata = msg.metadata.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.metadata.processingResults)
    val message = msg.copy(metadata = updatedMetadata)

    message match {
      case m @ Message(sysMsg: SystemMessage, _) => processSystemMessage(message.asInstanceOf[Message[SystemMessage]])
      case _ => handleMessage(message)
    }

  }

}

/**
 * Thrown when a SystemMessage processing exception occurs.
 * This can be used by the SupervisorStrategy to identify and handle a SystemMessage processing exception accordingly.
 */
class SystemMessageProcessingException(cause: Throwable) extends RuntimeException(cause) {}

class UnsupportedMessageTypeException(msg: Message[_]) extends RuntimeException {}

