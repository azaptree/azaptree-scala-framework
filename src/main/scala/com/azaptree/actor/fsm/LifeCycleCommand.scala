package com.azaptree.actor.fsm

sealed trait LifeCycleCommand

case object Initialize extends LifeCycleCommand

case object Start extends LifeCycleCommand

case object Stop extends LifeCycleCommand

case object Destroy extends LifeCycleCommand