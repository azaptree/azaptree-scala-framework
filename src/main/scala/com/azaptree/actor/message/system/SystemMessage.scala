package com.azaptree.actor.message.system

import com.azaptree.actor.message.Message

sealed trait SystemMessage

case object HeartbeatRequest extends SystemMessage {}

case object HeartbeatResponse extends SystemMessage {}

case object GetMessageStats extends SystemMessage {}

/**
 * Response message for GetStats
 */
case class MessageStats(
  messageCount: Long = 0l,
  lastMessageReceivedOn: Long = 0l,
  lastHeartbeatOn: Long = 0l) extends SystemMessage

case class MessageProcessedEvent(message: Message[_]) extends SystemMessage
