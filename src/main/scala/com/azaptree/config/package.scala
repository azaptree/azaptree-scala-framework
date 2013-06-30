package com.azaptree

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigException
import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory

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

  def getString(config: Config, path: String): Option[String] = {
    try {
      Some(config.getString(path))
    } catch {
      case e: ConfigException.Missing => None
      case e: Exception => throw e
    }
  }

  def wrapConfig(name: String, config: Config): Config = {
    val wrappedConfig = """"%s":%s""".format(name, toJson(config))
    ConfigFactory.parseString(wrappedConfig)
  }

  object ConfigConversions {
    val configConversionsLog = LoggerFactory.getLogger("com.azaptree.config.ConfigConversions");

    import scala.language.implicitConversions

    implicit def ComponentConfigInstance2Config(source: ComponentConfigInstance): Config = {
      val versionId = source.id.versionId
      val compId = versionId.compId
      val configJson = source.config.map("config " + toJson(_)).getOrElse("")
      val compDependencyRefs = source.compDependencyRefs.map { compDependencyRefs =>
        val sb = new StringBuilder(128)
        sb.append("component-dependency-refs = [")
        compDependencyRefs.foldLeft(sb) { (sb, compDependencyRef) =>
          val compId = compDependencyRef._2.versionId.compId
          sb.append(s"""{"group":"${compId.group}",
						 |"name":"${compId.name}",
						 |"config-ref-name":"${compDependencyRef._2.instance}",
						 |"config-name":"${compDependencyRef._1}"
						}\n""".stripMargin)
          sb
        }

        sb.append(']')
        sb
      }.getOrElse(new StringBuilder(0))

      val attributes = source.attributes.map { attrs =>
        val sb = new StringBuilder(128)
        sb.append("attributes = [")
        attrs.foldLeft(sb) { (sb, attr) =>
          sb.append("{name=\"").append(attr._1).append("\", value=\"").append(attr._2).append("\"}\n")
          sb
        }

        sb.append(']')
        sb
      }.getOrElse(new StringBuilder(0))

      val config = s"""{name = ${source.id.instance} \n ${compDependencyRefs} \n${attributes} \n ${configJson} }"""
      configConversionsLog.debug("config:\n{}", config)

      ConfigFactory.parseString(config)

    }

  }

}