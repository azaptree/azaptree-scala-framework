package com.azaptree.application

import scala.concurrent.Promise
import org.slf4j.LoggerFactory
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.azaptree.application.healthcheck.ApplicationHealthCheck

import com.azaptree.utils._
import java.io.PrintStream

trait ApplicationLauncher {
  def createApplicationService(): ApplicationService

  def logger = LoggerFactory.getLogger("com.azaptree.application.ApplicationLauncher")

  def launch(): Unit = {
    val appService = createApplicationService()

    val shutdownPromise = Promise[Unit]()
    val shutdownFuture = shutdownPromise.future
    val shutdownListener: Any => Unit = event => {
      event match {
        case event: PostApplicationShutdownEvent =>
          shutdownPromise.success(())
          logger.debug("PostApplicationShutdownEvent received")
        case _ =>
          logger.warn(s"Received unexpected event : $event")
      }
    }

    appService.eventBus.subscribe(shutdownListener, classOf[PostApplicationShutdownEvent])
    appService.start()
    Await.result(shutdownFuture, Duration.Inf)
    logger.debug("shutdownFuture is complete")
  }

}
object AppLauncher extends App {
  require(args.size > 0, "usage: scala com.azaptree.application.AppLauncher <ApplicationLauncher class name>")
  redirectStdOutErrToSl4j()
  try {
    launch()
    System.exit(0)
  } catch {
    case t: Throwable =>
      t.printStackTrace()
      System.exit(1)
  }

  def redirectStdOutErrToSl4j() = {
    val stdoutLog = LoggerFactory.getLogger("stdout")
    val stderrLog = LoggerFactory.getLogger("stderr")
    System.setOut(new PrintStream(System.out) {
      override def print(s: String) = {
        stdoutLog.info(s);
      }
    })

    System.setErr(new PrintStream(System.err) {
      override def print(s: String) = {
        stderrLog.error(s);
      }
    })
  }

  def launch() = {
    val log = LoggerFactory.getLogger("com.azaptree.application.AppLauncher")
    log.info("Application process runnings at : {}", PID_HOST)
    val launcherClass = args(0)
    val classLoader = getClass().getClassLoader()
    val launcher = classLoader.loadClass(launcherClass).newInstance().asInstanceOf[ApplicationLauncher]
    launcher.launch()
  }

}