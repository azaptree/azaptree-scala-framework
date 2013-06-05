package com.azaptree.actor.message

import akka.actor.SupervisorStrategy
import akka.event.LoggingReceive
import akka.actor.ReceiveTimeout

/**
 * Only supports messages of type: com.azaptree.actors.message.Message
 *
 * @author alfio
 *
 */
abstract class MessageActor extends MessageProcessor {

  /**
   * If actorConfig.loggingReceive = true, then the receive is wrapped in a akka.event.LoggingReceive which then logs message invocations.
   * This is enabled by a setting in the Configuration : akka.actor.debug.receive = on
   * *** NOTE: enabling it uniformly on all actors is not usually what you need, and it would lead to endless loops if it were applied to EventHandler listeners.
   */
  val executeReceive: Receive = {
    val processMessage: Receive = if (actorConfig.loggingReceive) {
      LoggingReceive {
        case msg: Message[_] => process(msg)
      }
    } else {
      case msg: Message[_] => process(msg)
    }

    val handleReceiveTimeout: PartialFunction[Any, Unit] = {
      case ReceiveTimeout => receiveTimeout()
    }

    processMessage orElse handleReceiveTimeout orElse unhandledMessage
  }

  /**
   *
   * MessageProcessor trait is used to process messages.
   *
   */
  override final def receive = { executeReceive }

}