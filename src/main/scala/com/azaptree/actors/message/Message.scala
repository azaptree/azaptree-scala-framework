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

case class ProcessingResult(
  actorPath: ActorPath,
  status: Option[MessageStatus] = None,
  metrics: MessageProcessingMetrics = MessageProcessingMetrics())

case class MessageProcessingMetrics(
  receivedOn: Long = System.currentTimeMillis,
  processingTime: Option[Long] = None)

case class MessageStatus(code: Int = 0, message: String = "success")

case class MessageProperties(
  messageId: UUID = UUID.randomUUID,
  createdOn: Long = System.currentTimeMillis)

/**
 * Higher the priorty value, the higher the priority.
 */
case class MessageHeader(
  durable: Boolean = false,
  priority: Byte = 0,
  deliveryCount: Int = 0,
  ttl: Duration = Duration.Inf)

object Heartbeat extends Serializable {}

object GetStats extends Serializable {}

case class MessageStats(
  successCount: Long = 0l,
  failureCount: Long = 0l,
  lastSuccessOn: Long = 0l,
  lastFailureOn: Long = 0l,
  lastHeartbeatOn: Long = 0l)
