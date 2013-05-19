package com.azaptree.actor.config

import akka.actor.Address
import akka.actor.Props

case class ActorConfig(
    name: String,
    routedTo: Boolean = false) {
}
