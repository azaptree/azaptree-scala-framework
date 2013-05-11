package com.azaptree.actors

import com.azaptree.actors.message.Message

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala
import com.azaptree.actors.message.Heartbeat

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

  override def receive = {
    case msg: Message[_] =>
      val message = msg.copy(actorPathChain = self.path :: msg.actorPathChain)
      msg.data match {
        case _: Heartbeat.type =>
          sender ! message
        case _ =>
          delegateToMessageHandler(message)
      }
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
        exceptionHandler.foreach(handler => handler(exception))
      }
    }
  }

}