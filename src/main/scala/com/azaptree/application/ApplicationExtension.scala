package com.azaptree.application

trait ApplicationListener {

  def start(): Unit

  def stop(): Unit
}

class ApplicationListenerComponentLifeCycle(l: ApplicationListener) extends ComponentLifeCycle[ApplicationListener] {

  override def create(comp: Component[ComponentNotConstructed, ApplicationListener]): Component[ComponentConstructed, ApplicationListener] = {
    comp.copy[ComponentConstructed, ApplicationListener](componentObject = Some(l))
  }

  override def start(comp: Component[ComponentInitialized, ApplicationListener]): Component[ComponentStarted, ApplicationListener] = {
    comp.componentObject.foreach(_.start())
    comp.copy[ComponentStarted, ApplicationListener]()
  }

  override def stop(comp: Component[ComponentStarted, ApplicationListener]): Component[ComponentStopped, ApplicationListener] = {
    comp.componentObject.foreach(_.stop())
    comp.copy[ComponentStopped, ApplicationListener]()
  }

}