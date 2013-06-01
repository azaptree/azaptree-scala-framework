package com.azaptree.actor.message

import akka.actor.ActorLogging
import akka.actor.Actor
import com.azaptree.actor.message.system.MessageProcessedEvent
import com.azaptree.actor.message.system.MessageStats

trait MessageLogging {
  self: Actor with ActorLogging =>

  protected[this] val stats = new MessageLoggingStats

  /**
   * logs the message, and then publishes a MessageEvent to the ActorSystem event stream
   */
  def logMessage(msg: Message[_]) = {
    context.system.eventStream.publish(MessageProcessedEvent(msg))
  }

  def messageReceived(): Unit = {
    stats.messageCount += 1
    stats.lastMessageReceivedOn = System.currentTimeMillis()
  }

  def messageProcessed(): Unit = {
    stats.lastMessageProcessedOn = System.currentTimeMillis()
  }

  def messageFailed(): Unit = {
    stats.lastMessageFailedOn = System.currentTimeMillis()
    stats.messageFailedCount += 1
  }

  def messageStats: MessageStats = { stats.messageStats }

}

class MessageLoggingStats {
  private[this] val actorCreatedOn: Long = System.currentTimeMillis()
  var messageCount: Long = 0l
  var lastMessageReceivedOn: Long = 0l
  var lastHeartbeatOn: Long = 0l
  /**
   * The last time a message finished processing without throwing an exception
   */
  var lastMessageProcessedOn: Long = 0l
  var messageFailedCount: Long = 0l
  var lastMessageFailedOn: Long = 0l

  def messageStats = {
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