package com.azaptree.application

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.azaptree.application.lifecycle.LifeCycle._

trait Component[A] {

  def name: String

  def instance: Option[A]

  def state: State

  protected def logger: Logger = { LoggerFactory.getLogger(s"Component[name]") }

  protected def create(): Component[A]

  protected def init(): Component[A]

  protected def start(): Component[A]

  protected def stop(): Component[A]

  /**
   * Starts up a new ComponentInstance instance.
   */
  final def startup(): Component[A] = {
    val compConstructed = create()
    val log = logger
    log.info("CONSTRUCTED")

    val compInitialized = compConstructed.init()
    log.info("INITIALIZED")

    val compStarted = compInitialized.start()
    log.info("STARTED")

    compStarted
  }

  final def shutdown(): Component[A] = {
    val log = logger

    val compStopped = stop()
    log.info("STOPPED")

    compStopped
  }
}

