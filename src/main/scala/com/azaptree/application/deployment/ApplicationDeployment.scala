package com.azaptree.application.deployment

import com.typesafe.config.Config
import java.io.File
import com.azaptree.config._
import java.net.URL
import com.azaptree.application.ApplicationExtension
import com.azaptree.application.pidFile.ApplicationPidFile
import com.azaptree.nio.file.FileWatcherService
import com.azaptree.application.ApplicationService
import com.azaptree.application.Component
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.application.ApplicationExtensionComponentLifeCycle
import com.azaptree.application.model.ApplicationInstanceId
import com.azaptree.application.model.ApplicationId
import com.azaptree.application.model.ApplicationVersionId
import applicationDeploymentConfigParams._

/**
 * Config Schema:
 * <code>
 *
 * com.azaptree{
 * 	app-instance-id{
 *  	group = "group"
 *      name = "name"
 *      version = "version"
 *      instance = "instance"
 *  }
 *
 *  config-service{
 *  	url = "http://localhost:8080/api/config-service/1-0-0/"${com.azaptree.app-instance-id.group}/${com.azaptree.app-instance-id.name}/${com.azaptree.app-instance-id.version}/${com.azaptree.app-instance-id.instance}"
 *  }
 * }
 * </code>
 */
case class ApplicationDeployment(appDeploymentConfig: ApplicationDeploymentConfig, namespace: String = "com.azaptree")(implicit fileWatcherService: FileWatcherService, applicationService: ApplicationService) extends ApplicationExtension {
  require(namespace.trim().length() > 0, "namespace is required")

  val config = appDeploymentConfig()

  val applicationInstanceId = com.azaptree.application.deployment.applicationInstanceId(config, Namespace(namespace))

  val appPidFile = ApplicationPidFile(this)

  override def start() = {
    appPidFile.start()
  }

  override def stop() = {
    appPidFile.stop()
  }

  /**
   * "${namespace}.base-dir" or falls back to JVM system property "user.dir"
   */
  def baseDir: File = {
    getString(config, s"${namespace}.base-dir") match {
      case None => new File(System.getProperty("user.dir"))
      case Some(dir) => new File(dir)
    }
  }

  /**
   * URL used to load the application instance Config
   */
  def configUrl: Option[String] = {
    getString(config, s"${namespace}.config-service.url")
  }

}

