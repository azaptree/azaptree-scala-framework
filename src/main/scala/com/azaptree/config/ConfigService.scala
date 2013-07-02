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

trait ConfigLookup {
  def config(): Config
}

trait ConfigService extends ApplicationConfigs {

  /**
   * The configuration instance is validated before returning
   */
  def applicationConfig(id: ApplicationInstanceId): Either[Exception, Option[Config]] = {
    def configWithDependencies(config: Config, configInstance: ApplicationInstanceConfig): Either[Exception, Option[Config]] = {
      configInstance.compDependencyRefs match {
        case None => Right(Some(config))
        case Some(compDependencyRefs) =>
          try {
            Right(Some(compDependencyRefs.foldLeft(config) { (c, compDependencyRef) =>
              componentConfig(compDependencyRef._2) match {
                case Left(e) => throw e
                case Right(None) => c
                case Right(Some(compConfig)) =>
                  c.withFallback(wrapConfig(compDependencyRef._1, compConfig))
              }
            }))
          } catch {
            case e: Exception => Left(e)
          }
      }
    }

    validate(id) match {
      case Some(e) => Left(e)
      case None =>
        applicationConfigInstance(id) match {
          case None => Right(None)
          case Some(appConfigInstance) =>
            appConfigInstance.config match {
              case None => configWithDependencies(ConfigFactory.empty(), appConfigInstance)
              case Some(appConfig) => configWithDependencies(appConfig, appConfigInstance)
            }
        }
    }
  }

  /**
   * The configuration instance is validated before returning
   */
  def componentConfig(id: ComponentInstanceId): Either[Exception, Option[Config]] = {
    def configWithDependencies(config: Config, compConfigInstance: ComponentInstanceConfig): Either[Exception, Option[Config]] = {
      compConfigInstance.compDependencyRefs match {
        case None => Right(Some(config))
        case Some(compDependencyRefs) =>
          try {
            Right(Some(compDependencyRefs.foldLeft(config) { (config, compDependencyRef) =>
              componentConfig(compDependencyRef._2) match {
                case Right(None) => config
                case Right(Some(compConfig)) =>
                  config.withFallback(wrapConfig(compDependencyRef._1, compConfig))
                case Left(e) => throw e
              }
            }))
          } catch {
            case e: Exception => Left(e)
          }
      }
    }

    validate(id) match {
      case None =>
        componentConfigInstance(id) match {
          case None => Right(None)
          case Some(compConfigInstance) =>
            compConfigInstance.config match {
              case None => configWithDependencies(ConfigFactory.empty(), compConfigInstance)
              case Some(config) => configWithDependencies(config, compConfigInstance)
            }
        }
      case Some(e) => Left(e)
    }

  }
}

