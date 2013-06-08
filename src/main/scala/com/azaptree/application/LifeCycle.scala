package com.azaptree.application.lifecycle

object LifeCycle {

  sealed trait State

  case object NotConstructed extends State
  case object Constructed extends State
  case object Initialized extends State
  case object Started extends State
  case object Stopped extends State
}

sealed trait ComponentState

case object ComponentNotConstructed extends ComponentState
case object ComponentConstructed extends ComponentState
case object ComponentInitialized extends ComponentState
case object ComponentStarted extends ComponentState
case object ComponentStopped extends ComponentState
