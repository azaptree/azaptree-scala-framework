package com.azaptree.actor.message

import akka.actor.SupervisorStrategy
import akka.event.LoggingReceive

/**
 * Only supports messages of type: com.azaptree.actors.message.Message
 *
 * Keeps track of the following metrics:
 * <ul>
 * <li> number of successfully processed messages
 * <li> number of unsuccessfully processed messages
 * <li> last time a message was processed successfully
 * <li> last time a message was processed unsuccessfully
 * </ul>
 *
 *
 *
 * If loggingReceive = true, then the receive is wrapped in a akka.event.LoggingReceive which then logs message invocations.
 * This is enabled by a setting in the Configuration : akka.actor.debug.receive = on
 * *** NOTE: enabling it uniformly on all actors is not usually what you need, and it would lead to endless loops if it were applied to EventHandler listeners.
 *
 * @author alfio
 *
 */
abstract class MessageActor extends MessageProcessor {

  val executeReceive: Receive = {
    val processMessage: Receive = if (actorConfig.loggingReceive) {
      LoggingReceive {
        case msg: Message[_] => process(msg)
      }
    } else {
      case msg: Message[_] => process(msg)
    }

    processMessage orElse unhandledMessage
  }

  /**
   * Handles the System messages automatically via SystemMessageProcessing.
   *
   * MessageProcessor trait is used to process application messages.
   *
   */
  override final def receive = { executeReceive }

}