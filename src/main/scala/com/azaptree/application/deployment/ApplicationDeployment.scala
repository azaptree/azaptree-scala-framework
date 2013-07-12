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
 * ${namespace}{
 * 	app-instance-id{
 *  	group = "group"
 *      name = "name"
 *      version = "version"
 *      instance = "instance"
 *  }
 * }
 * </code>
 *
 * where namespace is supplied and defaults to "com.azaptree", e.g.,
 * <code>
 * com.azaptree{
 * 	app-instance-id{
 *  	group = "group"
 *      name = "name"
 *      version = "version"
 *      instance = "instance"
 *  }
 * }
 * </code>
 *
 * fileWatcherService and applicationService are used by the ApplicationPidFile
 */
case class ApplicationDeployment(appDeploymentConfig: ApplicationDeploymentConfig, namespace: Namespace = Namespace())(implicit fileWatcherService: FileWatcherService, applicationService: ApplicationService) extends ApplicationExtension {

  val config = appDeploymentConfig()

  /**
   *
   */
  val applicationInstanceId = com.azaptree.application.deployment.applicationInstanceId(config, namespace)

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

}

