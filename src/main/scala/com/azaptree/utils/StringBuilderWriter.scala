package com.azaptree.utils

import java.io.Writer

/**
 * Provides a more efficient version than java.io.StringWriter.
 *
 * StringWriter uses a StringBuffer under the covers, which is synchronized and less efficient than using a StringBuilder
 */
class StringBuilderWriter(intialSize: Int = 32) extends Writer {
  var stringBuilder = new StringBuilder(intialSize)

  override def close(): Unit = {}

  override def flush(): Unit = {}

  override def write(cbuf: Array[Char], start: Int, length: Int): Unit = {
    for (i <- (start to (start + (length - 1)))) {
      stringBuilder += cbuf(i)
    }
  }

  override def toString(): String = stringBuilder.toString

}