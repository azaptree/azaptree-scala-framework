package com.azaptree.actor.message

import akka.event.LoggingReceive
import akka.actor.{ Actor, ActorRef }
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala
import com.azaptree.actor.message.system._
import com.azaptree.actor.ConfigurableActor
import com.azaptree.actor.config.ActorConfig
import akka.actor.SupervisorStrategy

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
abstract class MessageActor(config: ActorConfig) extends {
  override val actorConfig = config
} with ConfigurableActor
    with ActorLogging
    with SystemMessageProcessing
    with MessageLogging
    with MessageProcessor {

  override val supervisorStrategy = actorConfig.supervisorStrategy.getOrElse(SupervisorStrategy.defaultStrategy)

  val executeReceive: Receive = {
    if (actorConfig.loggingReceive) {
      LoggingReceive {
        case msg: Message[_] => process(msg)
      }
    } else {
      case msg: Message[_] => process(msg)
    }
  }

  /**
   * Handles the following system messages :
   * <ul>
   * <li>com.azaptree.actors.message.Heartbeat
   * <li>com.azaptree.actors.message.GetStats
   * </ul>
   *
   * All messages are logged after they are processed, which records the processing metrics and message processing status.
   * The Message status is set to ERROR_MESSAGE_STATUS if an exception is thrown during message processing.
   * If the message is successfully processed, i.e., no exception is thrown, but the Message status is None, then the Message status will be set to SUCCESS_MESSAGE_STATUS.
   *
   */
  override def receive = { executeReceive }

}