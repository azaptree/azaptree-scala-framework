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

  def update(status: MessageStatus): Message[A] = {
    copy(processingResults = processingResults.head.copy(status = Some(status)) :: processingResults.tail)
  }

  def update(status: MessageStatus, metrics: MessageProcessingMetrics): Message[A] = {
    copy(processingResults = processingResults.head.copy(status = Some(status), metrics = metrics) :: processingResults.tail)
  }

  def update(metrics: MessageProcessingMetrics): Message[A] = {
    copy(processingResults = processingResults.head.copy(metrics = metrics) :: processingResults.tail)
  }
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
  metrics: MessageProcessingMetrics = MessageProcessingMetrics()) {

  /**
   * calls update(SUCCESS_MESSAGE_STATUS)
   */
  def success() = {
    update(SUCCESS_MESSAGE_STATUS)
  }

  /**
   * calls update(ERROR_MESSAGE_STATUS)
   */
  def error() = {
    update(ERROR_MESSAGE_STATUS)
  }

  /**
   * Metrics processing time is also updated
   */
  def update(status: MessageStatus) = {
    copy(status = Some(status), metrics = metrics.updateProcessingTime)
  }

}

case class MessageStatus(code: Int = 0, message: String = "success")

case class MessageProcessingMetrics(receivedOn: Long = System.currentTimeMillis, processingTime: Option[Long] = None) {
  def updateProcessingTime = copy(processingTime = Some(System.currentTimeMillis - receivedOn))
}

