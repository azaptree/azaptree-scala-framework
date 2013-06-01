package com.azaptree.actor.config

import akka.actor.Address
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.actor.ActorPath

@SerialVersionUID(1L)
case class ActorConfig(
  name: String,
  routedTo: Boolean = false,
  loggingReceive: Boolean = false,
  supervisorStrategy: Option[SupervisorStrategy] = Some(DEFAULT_SUPERVISOR_STRATEGY))

