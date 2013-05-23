package com.azaptree.actor.message

import com.azaptree.actor.ConfigurableActor
import com.azaptree.actor.message.system.SystemMessage
import akka.actor.Actor
import akka.actor.ActorLogging

trait MessageProcessor {
  selfActor: ConfigurableActor with SystemMessageProcessing with MessageLogging with ActorLogging =>

  /**
   * Provides support to chain PartialFunctions for message processing.
   *
   * Subclasses will need to chain the partial functions using messageProcessingBuilder +=.
   *
   *
   */
  protected lazy val messageProcessingBuilder = new PartialFunctionBuilder[Message[_], Unit]

  /**
   * Sub-classes can override this method to provide the message handling logic.
   *
   * The Message status should be updated by this method.
   * If not set, then it will be set to SUCCESS_MESSAGE_STATUS if no exception was thrown, and set to ERROR_MESSAGE_STATUS if this method throws an Exception.
   *
   */

  var processMessage: PartialFunction[Message[_], Unit] = _

  /**
   * Builds the PartialFunction[Message[_], Unit] for processMessage.
   *
   * If subclasses override this method, make sure to call super.preStart()
   */
  override def preStart(): Unit = {
    processMessage = messageProcessingBuilder.result
  }

  /**
   * All exceptions are bubbled up to be handled by the SupervisorStrategy.
   * Exceptions that occur while processing SystemMessages will be wrapped in a SystemMessageProcessingException,
   * to enable detection and separate error handling for system message processing failures.
   */
  def process(msg: Message[_]): Unit = {

    def handleMessage(message: Message[_]) = {
      messageReceived()
      try {
        processMessage(message)
        messageProcessed()
        if (!message.metadata.processingResults.head.status.isDefined) {
          logMessage(message.update(status = SUCCESS_MESSAGE_STATUS))
        }
      } catch {
        case e: Exception =>
          messageFailed()
          logMessage(message.update(status = ERROR_MESSAGE_STATUS))
          throw e
      }
    }

    def handleSystemMessage(message: Message[SystemMessage]) = {
      try {
        processSystemMessage(message)
        if (!message.metadata.processingResults.head.status.isDefined) {
          log.info("{}", message.update(status = SUCCESS_MESSAGE_STATUS))
        }
      } catch {
        case e: SystemMessageProcessingException =>
          log.info("{}", message.update(status = ERROR_MESSAGE_STATUS))
          throw e
        case e: Exception =>
          log.info("{}", message.update(status = ERROR_MESSAGE_STATUS))
          throw new SystemMessageProcessingException(e)
      }
    }

    val updatedMetadata = msg.metadata.copy(processingResults = ProcessingResult(actorPath = self.path) :: msg.metadata.processingResults)
    val message = msg.copy(metadata = updatedMetadata)
    message.data match {
      case sysMsg: SystemMessage =>
        handleSystemMessage(message.asInstanceOf[Message[SystemMessage]])
      case _ =>
        handleMessage(message)
    }
  }

}

class SystemMessageProcessingException(cause: Throwable) extends RuntimeException(cause) {}

class PartialFunctionBuilder[A, B] {
  import scala.collection.immutable.Vector

  type PF = PartialFunction[A, B]

  private[this] var pfsOption: Option[Vector[PF]] = Some(Vector.empty)

  private[this] var processMessage: Option[PF] = None

  private def mapPfs[C](f: Vector[PF] ⇒ (Option[Vector[PF]], C)): C = {
    pfsOption.fold(throw new IllegalStateException("Already built"))(f) match {
      case (newPfsOption, result) ⇒ {
        pfsOption = newPfsOption
        result
      }
    }
  }

  def +=(pf: PF): Unit =
    mapPfs { case pfs ⇒ (Some(pfs :+ pf), ()) }

  def result: PF = {
    processMessage.getOrElse {
      assert(pfsOption.isDefined && pfsOption.get.size > 0, "At least one PartialFunction is required")
      val pf = mapPfs { case pfs ⇒ (None, pfs.foldLeft[PF](Map.empty) { _ orElse _ }) }
      processMessage = Some(pf)
      pf
    }
  }
}

