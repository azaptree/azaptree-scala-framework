package com.azaptree.actor.message

import com.azaptree.actor.ConfigurableActor
import com.azaptree.actor.message.system.SystemMessage
import akka.actor.Actor

trait MessageProcessor {
  selfActor: ConfigurableActor with SystemMessageProcessing with MessageLogging =>

  /**
   * Sub-classes override this method to provide the message handling logic.
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */
  protected[this] def processMessage(messageData: Any)(implicit message: Message[_]): Unit

  def process(implicit msg: Message[_]): Unit = {
    def execute(implicit message: Message[_]) = {
      messageReceived()
      try {
        processMessage(message.data)(message)
        messageProcessed()
        val metrics = message.processingResults.head.metrics.updated
        if (message.processingResults.head.status.isDefined) {
          logMessage(message.update(metrics = metrics))
        } else {
          logMessage(message.update(status = SUCCESS_MESSAGE_STATUS, metrics = metrics))
        }
      } catch {
        case e: Exception =>
          messageFailed()
          val metrics = message.processingResults.head.metrics.updated
          logMessage(message.update(status = ERROR_MESSAGE_STATUS, metrics = metrics))
          throw e
      }
    }

    implicit val message = msg.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.processingResults)
    message.data match {
      case sysMsg: SystemMessage =>
        processSystemMessage(sysMsg)(message)
      case _ =>
        execute(message)
    }
  }

}