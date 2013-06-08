package com.azaptree.application

import scala.reflect.ClassTag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.azaptree.application.lifecycle._

trait Application {
  def name: String

  def logger: Logger = { LoggerFactory.getLogger(s"Application[$name]") }

  def create(): ApplicationInstance[Constructed]

  def init(appCtx: ApplicationInstance[Constructed]): ApplicationInstance[Initialized]

  def start(appCtx: ApplicationInstance[Initialized]): ApplicationInstance[Started]

  def stop(appCtx: ApplicationInstance[Started]): ApplicationInstance[Stopped]

  /**
   * Starts up a new ApplicationInstance instance.
   */
  def startup(): ApplicationInstance[Started] = {
    val appConstructed = create()
    val log = logger
    log.info("CONSTRUCTED")

    val appInitialized = init(appConstructed)
    log.info("INITIALIZED")

    val appStarted = start(appInitialized)
    log.info("STARTED")

    appStarted
  }

  def shutdown(appCtx: ApplicationInstance[Started]): ApplicationInstance[Stopped] = {
    val log = logger

    val appStopped = stop(appCtx)
    log.info("STOPPED")

    appStopped

  }
}

trait ApplicationInstance[S <: State] {
  type Callback = () => Unit

  protected var initCallbacks: List[Callback] = Nil
  protected var startCallbacks: List[Callback] = Nil
  protected var stopCallbacks: List[Callback] = Nil
  protected var destroyCallbacks: List[Callback] = Nil

  def registerInitCallback(start: Callback) = {
    initCallbacks = start :: initCallbacks
  }

  def registerStartCallback(start: Callback) = {
    startCallbacks = start :: startCallbacks
  }

  def registerStopCallback(stop: Callback) = {
    stopCallbacks = stop :: stopCallbacks
  }

  def registerDestroyCallback(stop: Callback) = {
    destroyCallbacks = stop :: destroyCallbacks
  }

  def runInitCallbacks() = {
    initCallbacks.foreach(_())
  }

  def runStartCallbacks() = {
    startCallbacks.foreach(_())
  }

  def runStopCallbacks() = {
    stopCallbacks.foreach(_())
  }

  def runDestroyCallbacks() = {
    destroyCallbacks.foreach(_())
  }

}

case class TypeReference[A: ClassTag]()

case class ManagedObject[A: ClassTag](name: String, obj: A)

