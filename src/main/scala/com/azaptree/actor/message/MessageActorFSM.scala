package com.azaptree.actor.message

import com.azaptree.actor.fsm._
import com.azaptree.actor.message.system.SystemMessage
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.FSM
import akka.actor.Stash
import akka.actor.actorRef2Scala
import com.azaptree.actor.config.ActorConfig
import com.azaptree.actor.ConfigurableActor
import akka.actor.SupervisorStrategy
import akka.actor.DeadLetter
import akka.actor.UnhandledMessage
import akka.actor.ReceiveTimeout

/**
 * Only supports messages of types:
 *
 * <ol>
 * <li>com.azaptree.actors.message.Message
 * <li>com.azaptree.actors.fsm.LifeCycleCommand
 * <li>com.azaptree.actor.message.system.SystemMessage
 * </ol>
 *
 * Keeps track of the following metrics:
 * <ul>
 * <li> number of successfully processed messages
 * <li> number of unsuccessfully processed messages
 * <li> last time a message was processed successfully
 * <li> last time a message was processed unsuccessfully
 * </ul>
 *
 * Actor LifeCycle State Transitions:
 * <pre>
 * Constructed --Initialize-->  Idle
 * Idle --Start--> Running
 * Running --Stop--> Idle
 * Idle --Start--> Running
 * Running --Destroy--> Destroyed
 * </pre>
 *
 * @author alfio
 *
 */
abstract class MessageActorFSM extends MessageProcessor
    with Stash
    with FSM[State, Any] {

  /**
   * Override to perform intialization when transitioning from Constructed -> Idle.
   *
   * Default is a NOOP
   */
  def initializeActor(): Unit = {}

  /**
   * If the Message.data is a SystemMessage, then process it.
   * Otherwise, stash the message until we transition over to the "Running" state
   *
   */
  def stashMessage(msg: Message[_]): State = {
    val updatedMetadata = msg.metadata.copy(processingResults = ProcessingResult(senderActorPath = sender.path, actorPath = self.path) :: msg.metadata.processingResults)
    val message = msg.copy(metadata = updatedMetadata)
    message.data match {
      case sysMsg: SystemMessage =>
        processSystemMessage(msg.asInstanceOf[Message[SystemMessage]])
      case _ =>
        stash()
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
    case Event(msg: Message[_], _) =>
      process(msg)
      stay
  }

  whenUnhandled {
    case Event(Destroy, _) =>
      log.info("Destroy message was received - stopping Actor")
      stop
    case Event(ReceiveTimeout, _) =>
      receiveTimeout()
      stay
    case e: Event =>
      context.system.eventStream.publish(new UnhandledMessage(e, sender, context.self))
      stay
  }

  onTransition {
    case Constructed -> Idle =>
      initializeActor()
    case Idle -> Running => unstashAll()
  }

}
