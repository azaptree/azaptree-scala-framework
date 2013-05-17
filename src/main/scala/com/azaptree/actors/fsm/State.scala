package com.azaptree.actors.fsm

trait State

case object Constructed extends State

case object Idle extends State

case object Running extends State

case object Destroyed extends State

trait LifeCycle

case object Initialize extends LifeCycle

case object Start extends LifeCycle

case object Stop extends LifeCycle

case object Destroy extends LifeCycle