package com.azaptree.actor.message

import akka.actor.ActorLogging
import akka.actor.Actor
import com.azaptree.actor.message.system.MessageProcessedEvent
import com.azaptree.actor.message.system.MessageStats

trait MessageLogging {
  self: Actor with ActorLogging =>

  protected[this] val actorCreatedOn = System.currentTimeMillis()
  protected[this] var messageCount: Long = 0l
  protected[this] var messageFailedCount: Long = 0l
  protected[this] var lastMessageReceivedOn: Long = 0l
  /**
   * The last time a message finished processing without throwing an exception
   */
  protected[this] var lastMessageProcessedOn: Long = 0l
  protected[this] var lastMessageFailedOn: Long = 0l
  protected[this] var lastHeartbeatOn: Long = 0l

  /**
   * logs the message, and then publishes a MessageEvent to the ActorSystem event stream
   */
  def logMessage(msg: Message[_]) = {
    log.info("{}", msg)
    context.system.eventStream.publish(MessageProcessedEvent(msg))
  }

  def messageReceived(): Unit = {
    messageCount = messageCount + 1
    lastMessageReceivedOn = System.currentTimeMillis()
  }

  def messageProcessed(): Unit = {
    lastMessageProcessedOn = System.currentTimeMillis()
  }

  def messageFailed(): Unit = {
    lastMessageFailedOn = System.currentTimeMillis()
    messageFailedCount = messageFailedCount + 1
  }

  def messageStats: MessageStats = {
    MessageStats(
      actorCreatedOn = actorCreatedOn,
      messageCount = messageCount,
      lastMessageReceivedOn = if (lastMessageReceivedOn > 0l) Some(lastMessageReceivedOn) else None,
      lastMessageProcessedOn = if (lastMessageProcessedOn > 0l) Some(lastMessageProcessedOn) else None,
      lastHeartbeatOn = if (lastHeartbeatOn > 0l) Some(lastHeartbeatOn) else None,
      messageFailedCount = messageFailedCount,
      lastMessageFailedOn = if (lastMessageFailedOn > 0l) Some(lastMessageFailedOn) else None)
  }
}