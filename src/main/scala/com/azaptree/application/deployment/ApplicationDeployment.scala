package com.azaptree.application.deployment

import com.typesafe.config.Config
import java.io.File
import com.azaptree.config._
import java.net.URL

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
 *  	url = "http://localhost:8080/api/config-service/1-0-0/{com.azaptree.app-instance-id.group}/{com.azaptree.app-instance-id.name}/{com.azaptree.app-instance-id.version}/{com.azaptree.application-instance-id.instance}"
 *  }
 * }
 * </code>
 */
case class ApplicationDeployment(config: Config, namespace: String = "com.azaptree") {

  /**
   * "${namespace}.base-dir" or falls back to JVM system property "user.dir"
   */
  def baseDir: File = {
    getString(config, s"${namespace}.base-dir") match {
      case None => new File(System.getProperty("user.dir"))
      case Some(dir) => new File(dir)
    }
  }

  def applicationInstanceId: Option[ApplicationInstanceId] = {
    val appInstanceId = s"${namespace}.app-instance-id"
    for {
      group <- getString(config, s"$appInstanceId.group")
      name <- getString(config, s"$appInstanceId.name")
      version <- getString(config, s"$appInstanceId.version")
      instance <- getString(config, s"$appInstanceId.instance")
    } yield {
      ApplicationInstanceId(group = group, name = name, version = version, instance = instance)
    }
  }

  /**
   * URL used to load the application instance Config
   */
  def configUrl: Option[String] = {
    getString(config, s"${namespace}.config-service.url")
  }

}

case class ApplicationInstanceId(group: String, name: String, version: String, instance: String)