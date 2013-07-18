package com.azaptree.logging

import org.slf4j.LoggerFactory

trait Slf4jLogger {

  protected val log = LoggerFactory.getLogger(loggerName)

  def loggerName: String = getClass().getName()

}