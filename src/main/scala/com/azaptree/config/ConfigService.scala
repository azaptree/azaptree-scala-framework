package com.azaptree.config

import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory
import com.azaptree.application.model.ApplicationId
import com.azaptree.application.model.ApplicationVersion
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersion
import com.azaptree.application.model.ComponentVersionId
import com.azaptree.application.model.ComponentVersionId
import com.azaptree.application.model.ComponentVersionId
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.azaptree.application.model.ComponentDependency
import com.azaptree.application.model.ComponentDependencyConfig
import com.azaptree.application.model.ApplicationInstanceId
import com.azaptree.application.model.ComponentInstanceId
import scala.util.Try
import scala.util.Success
import scala.util.Failure

trait ConfigLookup {
  def config(): Config
}

trait ConfigService extends ApplicationConfigs {

  /**
   * The configuration instance is validated before returning
   */
  def applicationConfig(id: ApplicationInstanceId): Try[Option[Config]] = {
    def configWithDependencies(config: Config, configInstance: ApplicationInstanceConfig): Option[Config] = {
      configInstance.compDependencyRefs match {
        case None => Some(config)
        case Some(compDependencyRefs) =>
          Some(compDependencyRefs.foldLeft(config) { (c, compDependencyRef) =>
            componentConfig(compDependencyRef._2) match {
              case Failure(e) => throw e
              case Success(v) => v match {
                case None => c
                case Some(compConfig) =>
                  c.withFallback(wrapConfig(compDependencyRef._1, compConfig))
              }
            }
          })
      }
    }

    Try(
      validate(id) match {
        case Some(e) => throw e
        case None =>
          applicationConfigInstance(id) match {
            case None => None
            case Some(appConfigInstance) =>
              appConfigInstance.config match {
                case None => configWithDependencies(ConfigFactory.empty(), appConfigInstance)
                case Some(appConfig) => configWithDependencies(appConfig, appConfigInstance)
              }
          }
      })
  }

  /**
   * The configuration instance is validated before returning
   */
  def componentConfig(id: ComponentInstanceId): Try[Option[Config]] = {
    def configWithDependencies(config: Config, compConfigInstance: ComponentInstanceConfig): Option[Config] = {
      compConfigInstance.compDependencyRefs match {
        case None => Some(config)
        case Some(compDependencyRefs) =>
          Some(compDependencyRefs.foldLeft(config) { (config, compDependencyRef) =>
            componentConfig(compDependencyRef._2) match {
              case Success(value) => value match {
                case None => config
                case Some(compConfig) =>
                  config.withFallback(wrapConfig(compDependencyRef._1, compConfig))
              }
              case Failure(e) => throw e
            }
          })

      }
    }

    Try(
      validate(id) match {
        case None =>
          componentConfigInstance(id) match {
            case None => None
            case Some(compConfigInstance) =>
              compConfigInstance.config match {
                case None => configWithDependencies(ConfigFactory.empty(), compConfigInstance)
                case Some(config) => configWithDependencies(config, compConfigInstance)
              }
          }
        case Some(e) => throw e
      })

  }
}

