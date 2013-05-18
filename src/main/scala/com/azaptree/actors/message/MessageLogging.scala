package com.azaptree.actors.message

import akka.actor.ActorLogging
import akka.actor.Actor
import com.azaptree.actors.message.system.MessageProcessedEvent

trait MessageLogging {
  self: Actor with ActorLogging =>

  protected[this] var messageCount: Long = 0l
  protected[this] var lastMessageReceivedOn: Long = 0l
  protected[this] var lastHeartbeatOn: Long = 0l

  /**
   * logs the message, and then publishes a MessageEvent to the ActorSystem event stream
   */
  def logMessage(msg: Message[_]) = {
    log.info("{}", msg)
    context.system.eventStream.publish(MessageProcessedEvent(msg))
  }
}