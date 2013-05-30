package com.azaptree.actor

import akka.actor.Actor
import akka.actor.ActorRef
import com.azaptree.actor.config.ActorConfig
import akka.routing.NoRouter
import akka.actor.actorRef2Scala

trait ConfigurableActor extends Actor {

  def actorConfig: ActorConfig

  /**
   * Used to send messages to other Actors.
   *
   * If routed to, then the sender will be the parent, i.e., the head router.
   */
  val tell: (ActorRef, Any) => Unit =
    if (actorConfig.routedTo) {
      (actorRef: ActorRef, msg: Any) => actorRef.tell(msg, context.parent)
    } else {
      (actorRef: ActorRef, msg: Any) => actorRef ! msg
    }

}