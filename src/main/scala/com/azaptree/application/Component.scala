package com.azaptree.application

import org.slf4j.LoggerFactory

case class Component[S <: ComponentState, A](
    name: String,
    componentLifeCycle: ComponentLifeCycle[A],
    componentObject: Option[A] = None,
    dependsOn: Option[Iterable[Component[_, _]]] = None) {

  def startup(): Component[ComponentStarted, A] = {
    assert(componentObject.isEmpty, "It is invalid to startup a Component that is not in the NotConstructed state or already has some component object")

    componentLifeCycle.startUp(copy[ComponentNotConstructed, A]())
  }

  def shutdown(): Component[ComponentStopped, A] = {
    componentObject.foreach(o => {
      componentLifeCycle.shutdown(copy[ComponentStarted, A]())
    })

    copy[ComponentStopped, A](componentObject = None)
  }

}

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

  protected def stop(comp: Component[ComponentStarted, A]): Component[ComponentStopped, A] = comp.copy[ComponentStopped, A](componentObject = None)

  /**
   * This will startup a new instance of the component
   */
  final def startUp(comp: Component[ComponentNotConstructed, A]): Component[ComponentStarted, A] = {
    val log = LoggerFactory.getLogger(getClass())

    val constructed = comp.componentLifeCycle.create(comp)
    log.debug("ComponentConstructed : {}", comp.name)

    val initialized = constructed.componentLifeCycle.init(constructed)
    log.debug("ComponentInitialized : {}", comp.name)

    val started = initialized.componentLifeCycle.start(initialized)
    log.info("ComponentStarted : {}", comp.name)

    started
  }

  final def shutdown(comp: Component[ComponentStarted, A]): Component[ComponentStopped, A] = {
    val log = LoggerFactory.getLogger(getClass())
    val stopped = comp.componentLifeCycle.stop(comp)
    log.info("ComponentStopped : {}", comp.name)
    stopped
  }

}

