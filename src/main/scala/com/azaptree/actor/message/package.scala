package com.azaptree.actor

import akka.actor.SupervisorStrategy.Directive
import akka.actor.OneForOneStrategy
import scala.concurrent.duration.Duration
import akka.actor.SupervisorStrategy
import akka.actor.SupervisorStrategy._

package object message {
  val SUCCESS_MESSAGE_STATUS = MessageStatus()

  def error(exception: Exception) = {
    Error(errorType = exception.getClass().getName(), stackTrace = exception.getStackTraceString)
  }

  def unexpectedError(message: String = "Unexpected error", exception: Exception) = MessageStatus(code = 500, message = message, error = Some(error(exception)))

  def unsupportedMessageTypeError(message: Message[_]) = MessageStatus(code = 400, message = "Message Type is not supported: %s".format(message.data.getClass()))

}