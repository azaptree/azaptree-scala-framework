package com.azaptree.actor.config

import akka.actor.Address
import akka.actor.Props
import akka.actor.SupervisorStrategy

@SerialVersionUID(1L)
case class ActorConfig(
    name: String,
    routedTo: Boolean = false,
    loggingReceive: Boolean = false,
    supervisorStrategy: Option[SupervisorStrategy] = None) {
}
