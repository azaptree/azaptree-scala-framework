package com.azaptree.actor.message.system

import com.azaptree.actor.message.Message

import akka.actor.ActorPath

/**
 * System messages should be orthogonal to application messages.
 * System message processing should not hold up application message processing,
 * and should be handled by a child actor dedicated to system message processing
 */
sealed trait SystemMessage

@SerialVersionUID(1L)
case object HeartbeatRequest extends SystemMessage

@SerialVersionUID(1L)
case object HeartbeatResponse extends SystemMessage

@SerialVersionUID(1L)
case object GetMessageStats extends SystemMessage

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
  lastMessageFailedOn: Option[Long] = None)

@SerialVersionUID(1L)
case object GetActorConfig extends SystemMessage

@SerialVersionUID(1L)
case object GetChildrenActorPaths extends SystemMessage

/**
 * Response message for GetChildrenActorPaths
 */
@SerialVersionUID(1L)
case class ChildrenActorPaths(actorPaths: Iterable[ActorPath])

@SerialVersionUID(1L)
case class MessageProcessedEvent(message: Message[_]) extends SystemMessage

