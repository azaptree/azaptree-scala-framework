package com.azaptree.actors.message

import java.util.UUID

import scala.concurrent.duration.Duration

import akka.actor.ActorPath

/**
 * processingResults is the list of ProcessingResults from the Actor processing chain,
 * where the head of the list is ProcessingResult of the last Actor that received the message
 */
case class Message[A](
    data: A,
    properties: MessageProperties = MessageProperties(),
    header: Option[MessageHeader] = None,
    deliveryAnnotations: Option[Map[Symbol, Any]] = None,
    messageAnnotations: Option[Map[Symbol, Any]] = None,
    applicationProperties: Option[Map[Symbol, Any]] = None,
    processingResults: List[ProcessingResult] = Nil) {
}

case class MessageProperties(messageId: UUID = UUID.randomUUID, createdOn: Long = System.currentTimeMillis)

/**
 * Higher the priorty value, the higher the priority.
 */
case class MessageHeader(
  durable: Boolean = false,
  priority: Byte = 0,
  deliveryCount: Int = 0,
  ttl: Duration = Duration.Inf)

case class ProcessingResult(
  actorPath: ActorPath,
  status: Option[MessageStatus] = None,
  metrics: MessageProcessingMetrics = MessageProcessingMetrics())

case class MessageStatus(code: Int = 0, message: String = "success")

case class MessageProcessingMetrics(receivedOn: Long = System.currentTimeMillis, processingTime: Option[Long] = None)

trait SystemMessage extends Serializable

object Heartbeat extends SystemMessage {}

object GetStats extends SystemMessage {}

case class MessageStats(
  messageCount: Long = 0l,
  lastMessageReceivedOn: Long = 0l,
  lastHeartbeatOn: Long = 0l)

case class MessageEvent(message: Message[_])
