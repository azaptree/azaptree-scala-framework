package com.azaptree.actors

import com.azaptree.actors.message.SUCCESS_MESSAGE_STATUS
import com.azaptree.actors.message.GetStats
import com.azaptree.actors.message.Heartbeat
import com.azaptree.actors.message.Message
import com.azaptree.actors.message.MessageProcessingMetrics
import com.azaptree.actors.message.MessageStatus
import com.azaptree.actors.message.MessageStats
import com.azaptree.actors.message.ProcessingResult

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala

/**
 * Only supports messages of type: com.azaptree.actors.message.Message
 *
 * Keeps track of the following metrics:
 * <ul>
 * <li> number of successfully processed messages
 * <li> number of unsuccessfully processed messages
 * <li> last time a message was processed successfully
 * <li> last time a message was processed unsuccessfully
 * </ul>
 *
 */
abstract class ActorSupport extends Actor with ActorLogging {
  private[this] var successCount: Long = 0l
  private[this] var failureCount: Long = 0l
  private[this] var lastSuccessOn: Long = 0l
  private[this] var lastFailureOn: Long = 0l
  private[this] var lastHeartbeatOn: Long = 0l

  /**
   * Sub-classes override this method to provide the message handling logic
   *
   */
  def messageHandler: Receive

  /**
   * Optional exception handler
   *
   */
  def exceptionHandler: Option[PartialFunction[Exception, Unit]] = None

  /**
   * Handles the following system messages :
   * <ul>
   * <li>com.azaptree.actors.message.Heartbeat
   * <li>com.azaptree.actors.message.GetStats
   * </ul>
   */
  override def receive = {
    case msg: Message[_] =>
      val message = msg.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.processingResults)
      msg.data match {
        case _: Heartbeat.type =>
          processHeartbeat(message)
        case _: GetStats.type =>
          processGetStats(message)
        case _ =>
          try {
            delegateToMessageHandler(message)
          } catch {
            case e: Exception => exceptionHandler match {
              case Some(handler) => handler(e)
              case None => log.error(s"failed to process message : $message", e)
            }
          }
      }
  }

  def updateProcessingTime(metrics: MessageProcessingMetrics): MessageProcessingMetrics = {
    metrics.copy(processingTime = new Some(System.currentTimeMillis - metrics.receivedOn))
  }

  private[this] def processGetStats(message: Message[_]): Unit = {
    val metrics = updateProcessingTime(message.processingResults.head.metrics)
    sender ! Message[MessageStats](
      data = MessageStats(successCount, failureCount, lastSuccessOn, lastFailureOn, lastHeartbeatOn),
      processingResults = message.processingResults.head.copy(status = new Some(SUCCESS_MESSAGE_STATUS), metrics = metrics) :: message.processingResults.tail)
  }

  private[this] def processHeartbeat(message: Message[_]): Unit = {
    lastHeartbeatOn = System.currentTimeMillis
    val metrics = updateProcessingTime(message.processingResults.head.metrics)
    sender ! message.copy(processingResults = message.processingResults.head.copy(status = new Some(SUCCESS_MESSAGE_STATUS), metrics = metrics) :: message.processingResults.tail)
  }

  private[this] def delegateToMessageHandler(message: Message[_]): Unit = {
    try {
      messageHandler(message)
      successCount = successCount + 1
      lastSuccessOn = System.currentTimeMillis
    } catch {
      case exception: Exception => {
        failureCount = failureCount + 1
        lastFailureOn = System.currentTimeMillis
        exceptionHandler.foreach(_(exception))
      }
    }
  }

}