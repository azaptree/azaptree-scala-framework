package com.azaptree.actor.message

import java.util.UUID
import scala.concurrent.duration.Duration
import akka.actor.ActorPath
import akka.actor.ActorRef

/**
 * The intent for messageHeaders is similar to HTTP headers.
 *
 * processingResults is the list of ProcessingResults from the Actor processing chain,
 * where the head of the list is ProcessingResult of the last Actor that received the message
 */
@SerialVersionUID(1L)
case class Message[A](
    data: A,
    metadata: MessageMetadata = MessageMetadata()) {

  /**
   * updates the metrics as well, i.e., updates metrics.lastUpdatedOn
   */
  def update(status: MessageStatus): Message[A] = {
    copy(metadata = metadata.update(status))
  }

  def update(status: MessageStatus, metrics: MessageProcessingMetrics): Message[A] = {
    copy(metadata = metadata.update(status, metrics))
  }

  def update(metrics: MessageProcessingMetrics): Message[A] = {
    copy(metadata = metadata.update(metrics))
  }
}

@SerialVersionUID(1L)
case class MessageMetadata(messageId: UUID = UUID.randomUUID,
    createdOn: Long = System.currentTimeMillis,
    messageHeaders: Option[Map[Symbol, Any]] = None,
    processingResults: List[ProcessingResult] = Nil) {

  /**
   * updates the metrics as well, i.e., updates metrics.lastUpdatedOn
   */
  def update(status: MessageStatus): MessageMetadata = {
    val metrics = processingResults.head.metrics.updated
    update(status, metrics)
  }

  def update(status: MessageStatus, metrics: MessageProcessingMetrics): MessageMetadata = {
    copy(processingResults = processingResults.head.copy(status = Some(status), metrics = metrics) :: processingResults.tail)
  }

  def update(metrics: MessageProcessingMetrics): MessageMetadata = {
    copy(processingResults = processingResults.head.copy(metrics = metrics) :: processingResults.tail)
  }

}

@SerialVersionUID(1L)
case class ProcessingResult(
    senderActorPath: ActorPath,
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
    update(unexpectedError(exception = new Exception))
  }

  /**
   * Metrics processing time is also updated
   */
  def update(status: MessageStatus) = {
    copy(status = Some(status), metrics = metrics.updated)
  }

}

@SerialVersionUID(1L)
case class MessageStatus(code: Int = 0, message: String = "success", error: Option[Error] = None)

@SerialVersionUID(1L)
case class Error(errorType: String, stackTrace: String)

@SerialVersionUID(1L)
case class MessageProcessingMetrics(receivedOn: Long = System.currentTimeMillis, lastUpdatedOn: Option[Long] = None) {
  def updated = copy(lastUpdatedOn = Some(System.currentTimeMillis))

  def processingTime = lastUpdatedOn.getOrElse(System.currentTimeMillis()) - receivedOn
}

