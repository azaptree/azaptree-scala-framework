package com.azaptree.actor.message

import akka.actor.SupervisorStrategy
import akka.event.LoggingReceive
import akka.actor.ReceiveTimeout

/**
 * Handles messages that are wrapped within Message[A].
 * If a message is received that is not wrapped within a Message[A],
 * then it a new Message[A] envelope will be created for it and then processed.
 *
 * The main purpose of placing all messages within a Message[A] envelope is to track messages in a consistent manner.
 *
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