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
    val header: Option[MessageHeader],
    val deliveryAnnotations: Option[Map[Symbol, Any]],
    val messageAnnotations: Option[Map[Symbol, Any]],
    val applicationProperties: Option[Map[Symbol, Any]],
    val actorPathChain: List[ActorPath]) {
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
