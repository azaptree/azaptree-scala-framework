package com.azaptree.application

import org.slf4j.LoggerFactory

case class Component[s <: ComponentState, A](name: String, componentLifeCycle: ComponentLifeCycle[A], componentObject: Option[A] = None)

sealed trait ComponentState
sealed trait ComponentNotConstructed extends ComponentState
sealed trait ComponentConstructed extends ComponentState
sealed trait ComponentInitialized extends ComponentState
sealed trait ComponentStarted extends ComponentState
sealed trait ComponentStopped extends ComponentState

trait ComponentLifeCycle[A] {

  protected def create(comp: Component[ComponentNotConstructed, A]): Component[ComponentConstructed, A]

  protected def init(comp: Component[ComponentConstructed, A]): Component[ComponentInitialized, A] = comp.copy[ComponentInitialized, A]()

  protected def start(comp: Component[ComponentInitialized, A]): Component[ComponentStarted, A] = comp.copy[ComponentStarted, A]()

  protected def stop(comp: Component[ComponentStarted, A]): Component[ComponentStopped, A] = comp.copy[ComponentStopped, A]()

  /**
   * This will startup a new instance of the component
   */
  final def startUp(comp: Component[ComponentNotConstructed, A]): Component[ComponentStarted, A] = {
    val log = LoggerFactory.getLogger("%s.%s".format(getClass(), comp.name))

    val constructed = comp.componentLifeCycle.create(comp)
    log.debug("ComponentConstructed")

    val initialized = constructed.componentLifeCycle.init(constructed)
    log.debug("ComponentInitialized")

    val started = initialized.componentLifeCycle.start(initialized)
    log.info("ComponentStarted")

    started
  }

  final def shutdown(comp: Component[ComponentStarted, A]): Component[ComponentStopped, A] = {
    val log = LoggerFactory.getLogger("%s.%s".format(getClass(), comp.name))
    val stopped = comp.componentLifeCycle.stop(comp)
    log.info("ComponentStopped")
    stopped
  }

}

