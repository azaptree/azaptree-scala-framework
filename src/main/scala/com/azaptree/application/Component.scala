package com.azaptree.application

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.azaptree.application.lifecycle._
import akka.actor.ActorSystem

trait Component[C[S <: State] <: ComponentInstance[_]] {
  def name: String

  def logger: Logger = { LoggerFactory.getLogger(s"Component[$name]") }

  def create(): C[Constructed]

  def init(comp: C[Constructed]): C[Initialized]

  def start(comp: C[Initialized]): C[Started]

  def stop(comp: C[Started]): C[Stopped]

  /**
   * Starts up a new ComponentInstance instance.
   */
  def startup(): C[Started] = {
    val appConstructed = create()
    val log = logger
    log.info("CONSTRUCTED")

    val appInitialized = init(appConstructed)
    log.info("INITIALIZED")

    val appStarted = start(appInitialized)
    log.info("STARTED")

    appStarted
  }

  def shutdown(appCtx: C[Started]): C[Stopped] = {
    val log = logger

    val appStopped = stop(appCtx)
    log.info("STOPPED")

    appStopped
  }
}

trait ComponentInstance[S <: State] {
  type A

  type Callback = () => Unit

  def apply(): A

  def initCallback: Option[Callback] = None

  def startCallback: Option[Callback] = None

  def stopCallback: Option[Callback] = None
}
