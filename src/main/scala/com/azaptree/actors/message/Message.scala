package com.azaptree.actors.message

import java.util.UUID

import scala.concurrent.duration.Duration

import akka.actor.ActorPath

/**
 *
 * actorPathChain is the list of ActorPaths that this message has been sent to, where the first item in the list is the last path of the last Actor that received the message
 */
case class Message[A](
  val data: A,
  val properties: MessageProperties = MessageProperties(),
  val header: Option[MessageHeader] = None,
  val deliveryAnnotations: Option[Map[Symbol, Any]] = None,
  val messageAnnotations: Option[Map[Symbol, Any]] = None,
  val applicationProperties: Option[Map[Symbol, Any]] = None,
  val actorPathChain: List[ActorPath] = Nil) {
}

case class MessageProperties(
  val messageId: UUID = UUID.randomUUID,
  val createdOn: Long = System.currentTimeMillis)

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
  val successCount: Long = 0l,
  val failureCount: Long = 0l,
  val lastSuccessOn: Long = 0l,
  val lastFailureOn: Long = 0l,
  val lastHeartbeatOn: Long = 0l)
