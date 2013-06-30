package com.azaptree

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions
import com.typesafe.config.ConfigException
import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory
import scala.language.implicitConversions
import com.azaptree.application.model.ComponentDependency
import com.azaptree.application.model.ComponentInstanceId

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

    implicit def ApplicationConfigInstance2Config(source: ApplicationConfigInstance): Config = {
      val configJson = source.config.map(config => s"config ${toJson(config)}").getOrElse("")
      val compDependencyRefs = componentDependencyRefs2String(source.compDependencyRefs)
      val attributes = attributes2String(source.attributes)

      val config = s"""{name = ${source.id.instance}\n${compDependencyRefs}\n${attributes}\n${configJson} }"""
      configConversionsLog.debug("config:\n{}", config)

      ConfigFactory.parseString(config)
    }

    implicit def ComponentConfigInstance2Config(source: ComponentConfigInstance): Config = {
      val configJson = source.config.map(config => s"config ${toJson(config)}").getOrElse("")
      val compDependencyRefs = componentDependencyRefs2String(source.compDependencyRefs)

      val attributes = attributes2String(source.attributes)

      val config = s"""{name = ${source.id.instance}\n${compDependencyRefs}\n${attributes}\n${configJson} }"""
      configConversionsLog.debug("config:\n{}", config)

      ConfigFactory.parseString(config)

    }

    implicit def ApplicationVersionConfig2Config(source: ApplicationVersionConfig): Config = {
      val versionId = source.appVersion.id
      val configSchema = source.configSchema.map(configSchema => s"config-schema ${toJson(configSchema)}").getOrElse("")
      val configValidators = configValidators2String(source.validators)

      val config = s"""{"version":"${versionId.version}"\n${configSchema}\n${configValidators}\n${componentDependencies2String(source.appVersion.dependencies)}}"""
      configConversionsLog.debug("config:\n{}", config)

      ConfigFactory.parseString(config)
    }

    implicit def ComponentVersionConfig2Config(source: ComponentVersionConfig): Config = {
      val versionId = source.compVersion.id
      val configSchema = source.configSchema.map(configSchema => s"config-schema ${toJson(configSchema)}").getOrElse("")
      val configValidators = configValidators2String(source.validators)

      val compDependencies = componentDependencies2String(source.compVersion.compDependencies)

      val config = s"""{"version":"${versionId.version}"\n${compDependencies}\n${configSchema}\n${configValidators} }"""
      configConversionsLog.debug("config:\n{}", config)

      ConfigFactory.parseString(config)
    }

    private def componentDependencyRefs2String(compDependencyRefs: Option[Map[String, ComponentInstanceId]]): String = {
      compDependencyRefs.map { compDependencyRefs =>
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
      }.getOrElse(new StringBuilder(0)).toString()
    }

    private def configValidators2String(validators: Option[Iterable[ConfigValidator]]): String = {
      validators.map { validators =>
        val sb = new StringBuilder(128)
        sb.append("config-validators = [")
        validators.foldLeft(sb) { (sb, validator) =>
          sb.append(s""""${validator.getClass().getName()}"\n""")
        }
        sb.append(']')
        sb
      }.getOrElse(new StringBuilder(0)).toString()
    }

    private def attributes2String(attributes: Option[Map[String, String]]): String = {
      attributes.map { attrs =>
        val sb = new StringBuilder(128)
        sb.append("attributes = [")
        attrs.foldLeft(sb) { (sb, attr) =>
          sb.append("{name=\"").append(attr._1).append("\", value=\"").append(attr._2).append("\"}\n")
          sb
        }

        sb.append(']')
        sb
      }.getOrElse(new StringBuilder(0)).toString()
    }

    private def componentDependencies2String(compDependencies: Option[Iterable[ComponentDependency]]): String = {
      compDependencies.map { compDependencies =>
        val sb = new StringBuilder(256)
        sb.append("component-dependencies = [")

        compDependencies.foldLeft(sb) { (sb, compDependency) =>
          val compId = compDependency.compVersionId.compId
          sb.append(s"""{group="${compId.group}"
	        		  	|name="${compId.name}"
	        		  	|version="${compDependency.compVersionId.version}"""".stripMargin)

          compDependency.configs.map { configs =>
            sb.append("\nconfigs = [")
            configs.foldLeft(sb) { (sb, config) =>
              sb.append(s"""\n{name = "${config.name}"""");
              config.attributes.map { attributes =>
                sb.append("\nattributes = [")
                attributes.foldLeft(sb) { (sb, attr) =>
                  sb.append(s"""{name="${attr._1}", value="${attr._2}"}\n""")
                }
                sb.append(']') // close attributes[]
              }
              sb.append('}') // close config
            }
            sb.append(']') // close configs[]
          }

          sb.append("}\n") // close component dependency
        }
        sb.append(']')
        sb
      }.getOrElse(new StringBuilder(0)).toString()
    }

  }

}