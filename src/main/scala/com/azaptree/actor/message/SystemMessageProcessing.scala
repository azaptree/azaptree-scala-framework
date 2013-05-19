package com.azaptree.actor.message

import akka.actor.Actor
import com.azaptree.actor.message.system.GetStats
import com.azaptree.actor.message.system.HeartbeatResponse
import com.azaptree.actor.message.system.HeartbeatRequest
import com.azaptree.actor.message.system.MessageStats
import com.azaptree.actor.message.system.SystemMessage
import akka.actor.ActorLogging
import com.azaptree.actor.message.system.MessageProcessedEvent

trait SystemMessageProcessing {
  self: Actor with ActorLogging with MessageLogging =>

  def processSystemMessage(sysMsg: SystemMessage)(implicit message: Message[_]): Unit = {
    sysMsg match {
      case HeartbeatRequest =>
        processHeartbeat
      case GetStats =>
        processGetStats
      case _ => log.warning("Received unknown SystemMessage : {}", sysMsg)
    }
  }

  /**
   * Sends a Message[MessageStats] reply back to the sender.
   * The response message gets logged
   */
  def processGetStats(implicit message: Message[_]): Unit = {
    val responseData = MessageStats(messageCount, lastMessageReceivedOn, lastHeartbeatOn)
    val response = Message[MessageStats](
      data = responseData,
      processingResults = message.processingResults.head.success :: message.processingResults.tail)
    sender ! response
    logMessage(response)
  }

  /**
   * Sends a Message[HeartbeatResponse.type] reply back to the sender.
   * The response message gets logged.
   */
  def processHeartbeat(implicit message: Message[_]): Unit = {
    lastHeartbeatOn = System.currentTimeMillis
    val response = Message[HeartbeatResponse.type](
      data = HeartbeatResponse,
      processingResults = message.processingResults.head.success :: message.processingResults.tail)
    sender ! response
    logMessage(response)
  }

}