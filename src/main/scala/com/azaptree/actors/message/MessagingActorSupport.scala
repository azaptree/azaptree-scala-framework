package com.azaptree.actors.message

import akka.event.LoggingReceive
import akka.actor.{ Actor, ActorRef }
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala
import com.azaptree.actors.message.system._

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
 * routedTo is set to true if the Actor was created by a Router. This helps the Actor choose the sender reference for any messages they dispatch
 *
 * <code>
 * sender.tell(x, context.parent) // replies will go back to parent
 * sender ! x // replies will go to this actor
 * </code>
 *
 * @author alfio
 *
 */
abstract class MessagingActorSupport(routedTo: Boolean = false) extends Actor with ActorLogging {
  private[this] var messageCount: Long = 0l
  private[this] var lastMessageReceivedOn: Long = 0l
  private[this] var lastHeartbeatOn: Long = 0l

  /**
   * Sub-classes override this method to provide the message handling logic.
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */
  def processMessage(messageData: Any)(implicit message: Message[_]): Unit

  /**
   * if routed to, then the sender will be the parent, i.e., the head router
   */
  val tell =
    if (routedTo) {
      (actorRef: ActorRef, msg: Any) => actorRef.tell(msg, context.parent)
    } else {
      (actorRef: ActorRef, msg: Any) => actorRef ! msg
    }

  /**
   * Handles the following system messages :
   * <ul>
   * <li>com.azaptree.actors.message.Heartbeat
   * <li>com.azaptree.actors.message.GetStats
   * </ul>
   *
   * All messages are logged after they are processed, which records the processing metrics and message processing status.
   * The Message status is set to ERROR_MESSAGE_STATUS if an exception is thrown during message processing.
   * If the message is successfully processed, i.e., no exception is thrown, but the Message status is None, then the Message status will be set to SUCCESS_MESSAGE_STATUS.
   *
   */
  override def receive = LoggingReceive {
    case msg: Message[_] =>

      def processGetStats(implicit message: Message[_]): Unit = {
        val metrics = updateProcessingTime(message.processingResults.head.metrics)
        val responseData = MessageStats(messageCount, lastMessageReceivedOn, lastHeartbeatOn)
        val response = Message[MessageStats](
          data = responseData,
          processingResults = message.processingResults.head.copy(status = Some(SUCCESS_MESSAGE_STATUS), metrics = metrics) :: message.processingResults.tail)
        sender ! response
        logMessage(response)
      }

      /**
       * Replies to the Sender with
       */
      def processHeartbeat(implicit message: Message[_]): Unit = {
        lastHeartbeatOn = System.currentTimeMillis
        val metrics = updateProcessingTime(message.processingResults.head.metrics)
        val response = Message[HeartbeatResponse.type](
          data = HeartbeatResponse,
          processingResults = message.processingResults.head.copy(status = Some(SUCCESS_MESSAGE_STATUS), metrics = metrics) :: message.processingResults.tail)
        sender ! response
        logMessage(response)
      }

      def executeProcessMessage(implicit message: Message[_]) = {
        messageCount = messageCount + 1
        lastMessageReceivedOn = System.currentTimeMillis()
        try {
          processMessage(message.data)
          val metrics = updateProcessingTime(message.processingResults.head.metrics)
          if (message.processingResults.head.status.isDefined) {
            logMessage(message.update(metrics = metrics))
          } else {
            logMessage(message.update(status = SUCCESS_MESSAGE_STATUS, metrics = metrics))
          }
        } catch {
          case e: Exception =>
            val metrics = updateProcessingTime(message.processingResults.head.metrics)
            logMessage(message.update(status = ERROR_MESSAGE_STATUS, metrics = metrics))
            throw e
        }
      }

      /**
       * logs the message, and then publishes a MessageEvent to the ActorSystem event stream
       */
      def logMessage(msg: Message[_]) = {
        log.info("{}", msg)
        context.system.eventStream.publish(MessageProcessedEvent(msg))
      }

      def updateProcessingTime(metrics: MessageProcessingMetrics): MessageProcessingMetrics = {
        metrics.copy(processingTime = Some(System.currentTimeMillis - metrics.receivedOn))
      }

      implicit val message = msg.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.processingResults)
      message.data match {
        case HeartbeatRequest =>
          processHeartbeat
        case GetStats =>
          processGetStats
        case _ =>
          executeProcessMessage(message)
      }
  }

}