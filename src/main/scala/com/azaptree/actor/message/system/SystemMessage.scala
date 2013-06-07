package com.azaptree.actor.message.system

import com.azaptree.actor.message.Message
import akka.actor.ActorPath
import akka.actor.ActorRef

import scala.language.existentials

/**
 * System messages should be orthogonal to application messages.
 * System message processing should not hold up application message processing,
 * and should be handled by a child actor dedicated to system message processing
 */
sealed trait SystemMessage

@SerialVersionUID(1L)
case class MessageProcessedEvent(message: Message[_]) extends SystemMessage

sealed trait SystemMessageRequest extends SystemMessage

@SerialVersionUID(1L)
case object HeartbeatRequest extends SystemMessageRequest

@SerialVersionUID(1L)
case object GetMessageStats extends SystemMessageRequest

@SerialVersionUID(1L)
case object GetActorConfig extends SystemMessageRequest

@SerialVersionUID(1L)
case object GetChildrenActorPaths extends SystemMessageRequest

@SerialVersionUID(1L)
case object GetSystemMessageProcessorActorRef extends SystemMessageRequest

@SerialVersionUID(1L)
case class IsApplicationMessageSupported(message: Message[_]) extends SystemMessageRequest

sealed trait SystemMessageResponse extends SystemMessage

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
  lastMessageFailedOn: Option[Long] = None) extends SystemMessageResponse

/**
 * Response message for GetChildrenActorPaths
 */
@SerialVersionUID(1L)
case class ChildrenActorPaths(actorPaths: Iterable[ActorPath]) extends SystemMessageResponse

/**
 * Response message for GetSystemMessageProcessorActorRef
 */
@SerialVersionUID(1L)
case class SystemMessageProcessor(actorRef: ActorRef) extends SystemMessageResponse

/**
 * Response message for IsApplicationMessageSupported
 */
@SerialVersionUID(1L)
case class ApplicationMessageSupported(message: Message[_], supported: Boolean) extends SystemMessageResponse

@SerialVersionUID(1L)
case object HeartbeatResponse extends SystemMessageResponse

