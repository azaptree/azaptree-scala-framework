package com.azaptree.actors.message

import akka.actor.Actor
import com.azaptree.actors.message.system.GetStats
import com.azaptree.actors.message.system.HeartbeatResponse
import com.azaptree.actors.message.system.HeartbeatRequest
import com.azaptree.actors.message.system.MessageStats
import com.azaptree.actors.message.system.SystemMessage
import akka.actor.ActorLogging
import com.azaptree.actors.message.system.MessageProcessedEvent

trait SystemMessageProcessing {
  self: Actor with ActorLogging with MessageLogging =>

  def processSystemMessage(sysMsg: SystemMessage)(implicit message: Message[_]): PartialFunction[SystemMessage, Unit] = {
    case HeartbeatRequest =>
      processHeartbeat
    case GetStats =>
      processGetStats
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