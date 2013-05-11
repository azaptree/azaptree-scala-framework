package com.azaptree.actors.message

import akka.event.LoggingReceive
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
abstract class MessagingActorSupport extends Actor with ActorLogging {
  private[this] var messageCount: Long = 0l
  private[this] var lastMessageReceivedOn: Long = 0l
  private[this] var lastHeartbeatOn: Long = 0l

  /**
   * Sub-classes override this method to provide the message handling logic
   *
   */
  def processMessage(message: Message[_]): Unit

  /**
   * Handles the following system messages :
   * <ul>
   * <li>com.azaptree.actors.message.Heartbeat
   * <li>com.azaptree.actors.message.GetStats
   * </ul>
   */
  override def receive = LoggingReceive {
    case msg: Message[_] =>

      def processGetStats(message: Message[_]): Unit = {
        val metrics = updateProcessingTime(message.processingResults.head.metrics)
        val response = Message[MessageStats](
          data = MessageStats(messageCount, lastMessageReceivedOn, lastHeartbeatOn),
          processingResults = message.processingResults.head.copy(status = Some(SUCCESS_MESSAGE_STATUS), metrics = metrics) :: message.processingResults.tail)
        sender ! response
        logMessage(response)
      }

      def processHeartbeat(message: Message[_]): Unit = {
        lastHeartbeatOn = System.currentTimeMillis
        val metrics = updateProcessingTime(message.processingResults.head.metrics)
        val response = message.copy(processingResults = message.processingResults.head.copy(status = Some(SUCCESS_MESSAGE_STATUS), metrics = metrics) :: message.processingResults.tail)
        sender ! response
        logMessage(response)
      }

      val message = msg.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.processingResults)
      msg.data match {
        case _: Heartbeat.type =>
          processHeartbeat(message)
        case _: GetStats.type =>
          processGetStats(message)
        case _ =>
          delegateMessageProcessing(message)
      }
  }

  def delegateMessageProcessing(message: Message[_]) = {
    messageCount = messageCount + 1
    lastMessageReceivedOn = System.currentTimeMillis()
    try {
      processMessage(message)
      val metrics = updateProcessingTime(message.processingResults.head.metrics)
      logMessage(message.copy(processingResults = message.processingResults.head.copy(status = Some(SUCCESS_MESSAGE_STATUS), metrics = metrics) :: message.processingResults.tail))
    } catch {
      case e: Exception =>
        val metrics = updateProcessingTime(message.processingResults.head.metrics)
        logMessage(message.copy(processingResults = message.processingResults.head.copy(status = Some(ERROR_MESSAGE_STATUS), metrics = metrics) :: message.processingResults.tail))
        throw e
    }
  }

  /**
   * logs the message, and then publishes a MessageEvent to the ActorSystem event stream
   */
  def logMessage(msg: Message[_]) = {
    log.info("{}", msg)
    context.system.eventStream.publish(MessageEvent(msg))
  }

  def updateProcessingTime(metrics: MessageProcessingMetrics): MessageProcessingMetrics = {
    metrics.copy(processingTime = Some(System.currentTimeMillis - metrics.receivedOn))
  }

}