package com.azaptree.concurrent

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinWorkerThread

case class ConfigurableForkJoinWorkerThreadFactory(
  threadBaseName: Option[String] = None,
  daemon: Option[Boolean] = None,
  uncaughtExceptionHandler: Option[Thread.UncaughtExceptionHandler] = None,
  priority: Option[Int] = None)
    extends ForkJoinPool.ForkJoinWorkerThreadFactory {

  private[this] val count = new AtomicInteger(0)

  override def newThread(p: ForkJoinPool): ForkJoinWorkerThread = {
    val thread = threadBaseName match {
      case Some(name) =>
        val n = count.incrementAndGet()
        val t = new ConfigurableForkJoinWorkerThread(p)
        t.setName(s"$name-$n")
        t
      case None => new ConfigurableForkJoinWorkerThread(p)
    }

    daemon.foreach(thread.setDaemon(_))
    uncaughtExceptionHandler.foreach(thread.setUncaughtExceptionHandler(_))
    priority.foreach(thread.setPriority(_))
    thread
  }
}

class ConfigurableForkJoinWorkerThread(p: ForkJoinPool) extends ForkJoinWorkerThread(p)

