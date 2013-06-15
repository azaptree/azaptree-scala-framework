package com.azaptree

import java.io.PrintWriter
import java.lang.management.ManagementFactory
import java.lang.Long
package object utils {

  /** PID@HOST */
  val PID_HOST: String = ManagementFactory.getRuntimeMXBean().getName()
  val HOST: String = PID_HOST.substring(PID_HOST.indexOf('@') + 1)
  val PID: Long = Long.parseLong(PID_HOST.substring(0, PID_HOST.indexOf('@')))

  def getExceptionStackTrace(t: Throwable): String = {
    val sw = new StringBuilderWriter()
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}