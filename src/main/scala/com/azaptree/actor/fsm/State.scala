package com.azaptree.actor.fsm

sealed trait State

case object Constructed extends State

case object Idle extends State

case object Running extends State

case object Destroyed extends State

sealed trait LifeCycleCommand

case object Initialize extends LifeCycleCommand

case object Start extends LifeCycleCommand

case object Stop extends LifeCycleCommand

case object Destroy extends LifeCycleCommand