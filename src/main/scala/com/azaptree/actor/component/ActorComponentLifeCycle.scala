package com.azaptree.actor.component

import com.azaptree.actor.config.ActorConfig
import com.azaptree.application.Component
import com.azaptree.application.ComponentConstructed
import com.azaptree.application.ComponentInitialized
import com.azaptree.application.ComponentLifeCycle
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.application.ComponentStarted
import com.azaptree.application.ComponentStopped
import akka.actor.ActorRef
import akka.actor.ActorSystem
import com.azaptree.actor.ActorSystemManager
import akka.actor.Kill

case class ActorComponentLifeCycle(actorConfig: ActorConfig)(implicit actorSystem: ActorSystem) extends ComponentLifeCycle[ActorRef] {

  override protected def create(comp: Component[ComponentNotConstructed, ActorRef]): Component[ComponentConstructed, ActorRef] = {
    val actorRef = actorConfig.actorOfActorSystem
    comp.copy[ComponentConstructed, ActorRef](componentObject = Some(actorRef))
  }

  override protected def stop(comp: Component[ComponentStarted, ActorRef]): Component[ComponentStopped, ActorRef] = {
    actorConfig.gracefulStopTimeout match {
      case Some(gracefulStopTimeout) => ActorSystemManager.gracefulStop(comp.componentObject.get, gracefulStopTimeout)
      case None => actorConfig.stopMessage match {
        case Some(stopMessage) => comp.componentObject.get ! stopMessage
        case None => comp.componentObject.get ! Kill
      }
    }
    comp.copy[ComponentStopped, ActorRef](componentObject = None)
  }

}