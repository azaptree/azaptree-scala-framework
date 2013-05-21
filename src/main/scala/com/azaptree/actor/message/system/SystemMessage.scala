package com.azaptree.actor.message.system

import com.azaptree.actor.message.Message

sealed trait SystemMessage

@SerialVersionUID(1L)
case object HeartbeatRequest extends SystemMessage {}

@SerialVersionUID(1L)
case object HeartbeatResponse extends SystemMessage {}

@SerialVersionUID(1L)
case object GetMessageStats extends SystemMessage {}

/**
 * Response message for GetStats
 */
@SerialVersionUID(1L)
case class MessageStats(
  actorCreatedOn: Long,
  messageCount: Long = 0l,
  lastMessageReceivedOn: Option[Long] = None,
  lastHeartbeatOn: Option[Long] = None,
  lastMessageProcessedOn: Option[Long] = None,
  messageFailedCount: Long = 0,
  lastMessageFailedOn: Option[Long] = None) extends SystemMessage

@SerialVersionUID(1L)
case class MessageProcessedEvent(message: Message[_]) extends SystemMessage
