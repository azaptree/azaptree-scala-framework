package com.azaptree.actors.message

import com.azaptree.actors.fsm._
import com.azaptree.actors.message.system._

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.{ FSM, LoggingFSM }
import akka.actor.Stash
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
abstract class MessagingActorFSMSupport(routedTo: Boolean = false) extends Actor with Stash with LoggingFSM[State, Any] {
  private[this] var messageCount: Long = 0l
  private[this] var lastMessageReceivedOn: Long = 0l
  private[this] var lastHeartbeatOn: Long = 0l

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
   * Sub-classes override this method to provide the message handling logic.
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */
  def processMessage(messageData: Any)(implicit message: Message[_]): Unit

  /**
   * Override to perform intialization when transitioning from Constructed -> Idle.
   *
   * Default is a NOOP
   */
  def initializeActor(): Unit = {}

  def processGetStats(implicit message: Message[_]): Unit = {
    val metrics = updateProcessingTime(message.processingResults.head.metrics)
    val response = Message[MessageStats](
      data = MessageStats(messageCount, lastMessageReceivedOn, lastHeartbeatOn),
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

  def handleSystemMessage(sysMsg: SystemMessage)(implicit message: Message[_]): PartialFunction[SystemMessage, Unit] = {
    case HeartbeatRequest =>
      processHeartbeat
    case GetStats =>
      processGetStats
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

  def stashMessage(msg: Message[_]): State = {
    implicit val message = msg.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.processingResults)
    message.data match {
      case sysMsg: SystemMessage =>
        handleSystemMessage(sysMsg)
      case _ =>
        stash()
    }
    stay
  }

  def processMessage(msg: Message[_]): State = {

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

    implicit val message = msg.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.processingResults)
    message.data match {
      case sysMsg: SystemMessage =>
        handleSystemMessage(sysMsg)
      case _ =>
        executeProcessMessage(message)
    }
    stay
  }

  startWith(Constructed, None)

  when(Constructed) {
    case Event(Initialize, _) => goto(Idle)
    case Event(msg: Message[_], _) => stashMessage(msg)
  }

  when(Idle) {
    case Event(Start, _) => goto(Running)
    case Event(msg: Message[_], _) => stashMessage(msg)
  }

  when(Running) {
    case Event(Stop, _) => goto(Idle)
    case Event(msg: Message[_], _) => processMessage(msg)
  }

  whenUnhandled {
    case Event(Destroy, _) =>
      log.info("Destroy message was received - stopping Actor")
      stop
    case e: Event =>
      log.warning("Invalid message was received while in state [{}] : {}", stateName, e.event)
      stay
  }

  onTransition {
    case Constructed -> Idle => initializeActor()
    case Idle -> Running => unstashAll()
  }

}
