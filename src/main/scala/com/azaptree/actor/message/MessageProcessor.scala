package com.azaptree.actor.message

import com.azaptree.actor.ConfigurableActor
import com.azaptree.actor.message.system.SystemMessage
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.DeadLetter
import akka.actor.Props
import akka.actor.ActorRef
import SystemMessageProcessorActor._
import akka.actor.UntypedActorFactory

trait MessageProcessor extends ConfigurableActor with MessageLogging {

  /**
   * Creates the SystemMessageProcessorActor child Actor
   */
  override def preStart() = {
    super.preStart()
    createSystemMessageProcessorActor
  }

  def createSystemMessageProcessorActor: ActorRef = {
    context.actorOf(Props(new SystemMessageProcessorActor(context, this)), SYSTEM_MESSAGE_PROCESSOR_ACTOR_NAME)
  }

  val processApplicationMessage = processMessage orElse (handleInvalidMessage andThen unsupportedMessageTypeException)

  /**
   * Sub-classes can override this method to provide the message handling logic.
   * This should handle all Message where Message.data is not of type: SystemMessage
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */
  def processMessage: PartialFunction[Message[_], Unit]

  def processSystemMessage(message: Message[SystemMessage]) = {
    val systemMessageProcessingActor = context.child(SYSTEM_MESSAGE_PROCESSOR_ACTOR_NAME).getOrElse(createSystemMessageProcessorActor)
    systemMessageProcessingActor.forward(message.update(status = SUCCESS_MESSAGE_STATUS))
  }

  /**
   * Records that that the message failed and logs a DeadLetter to the ActorSystem.eventStream
   *
   */
  def handleInvalidMessage: PartialFunction[Any, Unit] = {
    case msg =>
      messageFailed()
      context.system.eventStream.publish(new DeadLetter(msg, sender, context.self))
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

}

@SerialVersionUID(1L)
class UnsupportedMessageTypeException() extends RuntimeException {}

