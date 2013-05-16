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
   * Sub-classes override this method to provide the message handling logic
   *
   */
  def processMessage(message: Message[_]): Unit

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
        val response = message.update(status = SUCCESS_MESSAGE_STATUS, metrics = metrics)
        sender ! response
        logMessage(response)
      }

      val message = msg.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.processingResults)
      msg.data match {
        case Heartbeat =>
          processHeartbeat(message)
        case GetStats =>
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
      logMessage(message.update(metrics = metrics))
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

}