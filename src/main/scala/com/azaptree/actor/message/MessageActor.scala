package com.azaptree.actor.message

import akka.actor.SupervisorStrategy
import akka.event.LoggingReceive
import akka.actor.ReceiveTimeout

/**
 *
 * <ul>Only supports messages of type:
 * <li>com.azaptree.actors.message.Message
 * <li>akka.actor.Terminated - which will be wrapped in a Message[Terminated] before processing it
 * </ul>
 *
 * @author alfio
 *
 */
abstract class MessageActor extends MessageProcessor {

  /**
   *
   * MessageProcessor trait is used to process messages.
   *
   */
  override final val receive = process

}