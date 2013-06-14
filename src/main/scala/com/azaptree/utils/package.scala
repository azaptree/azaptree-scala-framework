package com.azaptree

import java.io.PrintWriter
package object utils {

  def getExceptionStackTrace(t: Throwable): String = {
    val sw = new StringBuilderWriter()
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }
}