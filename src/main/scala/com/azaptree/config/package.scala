package com.azaptree

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigException
import scala.collection.JavaConversions._

package object config {

  lazy val globalConfig: Config = ConfigFactory.load()

  val jsonFormattedRenderOptions = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)

  val jsonRenderOptions = jsonFormattedRenderOptions.setFormatted(false)

  def toJson(config: Config): String = {
    config.root().render(jsonRenderOptions)
  }

  def toFormattedJson(config: Config): String = {
    config.root().render(jsonFormattedRenderOptions)
  }

  /**
   * the nested Config value at the requested path, or none if the path does not exist
   */
  def getConfig(config: Config, path: String): Option[Config] = {
    try {
      Some(config.getConfig(path))
    } catch {
      case e: ConfigException.Missing => None
      case e: Exception => throw e
    }
  }

  def getConfigList(config: Config, path: String): Option[Seq[Config]] = {
    try {
      Some(config.getConfigList(path))
    } catch {
      case e: ConfigException.Missing => None
      case e: Exception => throw e
    }
  }

  def getStringList(config: Config, path: String): Option[Seq[String]] = {
    try {
      Some(config.getStringList(path))
    } catch {
      case e: ConfigException.Missing => None
      case e: Exception => throw e
    }
  }

}