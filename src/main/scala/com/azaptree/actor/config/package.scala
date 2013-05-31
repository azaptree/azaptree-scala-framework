package com.azaptree.actor

import akka.actor.OneForOneStrategy
import scala.concurrent.duration.Duration
import com.azaptree.actor.message.UnsupportedMessageTypeException
import akka.actor.SupervisorStrategy._
import akka.actor.SupervisorStrategy

package object config {

  val unsupportedMessageTypeExceptionDecider: PartialFunction[Throwable, Directive] = { case e: UnsupportedMessageTypeException => Resume }

  val DEFAULT_SUPERVISOR_STRATEGY = OneForOneStrategy(maxNrOfRetries = Int.MaxValue, withinTimeRange = Duration.Inf) {
    unsupportedMessageTypeExceptionDecider orElse SupervisorStrategy.defaultStrategy.decider
  }

}