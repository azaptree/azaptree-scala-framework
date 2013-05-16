package com.azaptree.actors.message.system

import com.azaptree.actors.message.Message

sealed trait SystemMessage

case object Heartbeat extends SystemMessage {}

case object GetStats extends SystemMessage {}

case class MessageStats(
  messageCount: Long = 0l,
  lastMessageReceivedOn: Long = 0l,
  lastHeartbeatOn: Long = 0l) extends SystemMessage

case class MessageProcessedEvent(message: Message[_]) extends SystemMessage
