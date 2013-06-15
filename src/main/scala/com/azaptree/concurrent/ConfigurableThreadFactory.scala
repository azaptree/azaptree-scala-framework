package com.azaptree.concurrent

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

case class ConfigurableThreadFactory(
  threadBaseName: Option[String] = None,
  daemon: Option[Boolean] = None,
  uncaughtExceptionHandler: Option[Thread.UncaughtExceptionHandler] = None,
  priority: Option[Int] = None)
    extends ThreadFactory {

  private[this] val count = new AtomicInteger(0)

  override def newThread(r: Runnable): Thread = {
    val thread = threadBaseName match {
      case Some(name) =>
        val n = count.incrementAndGet()
        new Thread(r, s"$name-$n")
      case None => new Thread(r)
    }

    daemon.foreach(thread.setDaemon(_))
    uncaughtExceptionHandler.foreach(thread.setUncaughtExceptionHandler(_))
    priority.foreach(thread.setPriority(_))
    thread
  }

}