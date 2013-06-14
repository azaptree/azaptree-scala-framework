package com.azaptree.application

import ApplicationService._
import scala.concurrent.Promise
import org.slf4j.LoggerFactory
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.azaptree.application.healthcheck.ApplicationHealthCheck

case class ApplicationServiceConfig(compCreator: ComponentCreator, asyncEventBus: Boolean = true, applicationHealthChecks: Option[List[ApplicationHealthCheck]] = None)

trait ApplicationLauncher {
  def createApplicationServiceConfig(): ApplicationServiceConfig

  def launch(): Unit = {
    val appServiceConfig = createApplicationServiceConfig()
    val appService = new ApplicationService(appServiceConfig.compCreator, appServiceConfig.asyncEventBus)

    val shutdownPromise = Promise[Unit]()
    val shutdownFuture = shutdownPromise.future

    val shutdownListener: (Any) => Unit = event => {
      event match {
        case PostApplicationShutdownEvent =>
          shutdownPromise.success(())
        case _ =>
          val log = LoggerFactory.getLogger(getClass())
          log.warn(s"Received unexpected event : $event")
      }
    }

    appService.subscribe(shutdownListener, classOf[PostApplicationShutdownEvent])

    appService.start()

    Await.result(shutdownFuture, Duration.Inf)

  }

}

object AppLauncher extends App {
  require(args.size > 0, "usage: scala com.azaptree.application.AppLauncher <ApplicationLauncher class name>")
  val launcherClass = args(0)
  val classLoader = getClass().getClassLoader()
  val launcher = classLoader.loadClass(launcherClass).newInstance().asInstanceOf[ApplicationLauncher]
  launcher.launch()
}