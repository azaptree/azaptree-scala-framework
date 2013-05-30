package com.azaptree.actor

import akka.actor.OneForOneStrategy
import scala.concurrent.duration.Duration
import com.azaptree.actor.message.UnsupportedMessageTypeException
import akka.actor.SupervisorStrategy._
import akka.actor.SupervisorStrategy

package object config {

  val defaultSupervisorStrategy = OneForOneStrategy(maxNrOfRetries = Int.MaxValue, withinTimeRange = Duration.Inf)(defaultDecider)

  private[this] val unsupportedMessageTypeExceptionDecider: PartialFunction[Throwable, Directive] = { case e: UnsupportedMessageTypeException => Resume }

  private[this] val defaultDecider = unsupportedMessageTypeExceptionDecider orElse SupervisorStrategy.defaultStrategy.decider
}