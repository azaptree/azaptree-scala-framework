package com.azaptree.actor.message

import akka.event.LoggingReceive
import akka.actor.{ Actor, ActorRef }
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala
import com.azaptree.actor.message.system._
import com.azaptree.actor.ConfigurableActor
import com.azaptree.actor.config.ActorConfig

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
 *
 *
 * If loggingReceive = true, then the receive is wrapped in a akka.event.LoggingReceive which then logs message invocations.
 * This is enabled by a setting in the Configuration : akka.actor.debug.receive = on
 * *** NOTE: enabling it uniformly on all actors is not usually what you need, and it would lead to endless loops if it were applied to EventHandler listeners.
 *
 * @author alfio
 *
 */
abstract class MessagingActor(actorConfig: ActorConfig, loggingReceive: Boolean = false) extends ConfigurableActor(actorConfig)
    with ActorLogging
    with SystemMessageProcessing
    with MessageLogging {

  /**
   * Sub-classes override this method to provide the message handling logic.
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */
  def processMessage(messageData: Any)(implicit message: Message[_]): Unit

  val executeReceive: Receive = {
    def receiveMessage: Receive = {
      case msg: Message[_] =>

        def executeProcessMessage(implicit message: Message[_]) = {
          messageCount = messageCount + 1
          lastMessageReceivedOn = System.currentTimeMillis()
          try {
            processMessage(message.data)
            if (message.processingResults.head.status.isDefined) {
              if (message.processingResults.head.metrics.lastUpdatedOn.isDefined) {
                logMessage(message)
              } else {
                logMessage(message.update(metrics = message.processingResults.head.metrics.updated))
              }
            } else {
              logMessage(message.update(status = SUCCESS_MESSAGE_STATUS))
            }
          } catch {
            case e: Exception =>
              logMessage(message.update(status = ERROR_MESSAGE_STATUS))
              throw e
          }
        }

        implicit val message = msg.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.processingResults)
        message.data match {
          case sysMsg: SystemMessage =>
            processSystemMessage(sysMsg)
          case _ =>
            executeProcessMessage(message)
        }
    }

    if (loggingReceive) {
      LoggingReceive { receiveMessage }
    } else {
      receiveMessage
    }

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
  override def receive = { executeReceive }

}