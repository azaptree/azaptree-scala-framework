package com.azaptree.application

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.azaptree.config._
import com.azaptree.application.model._
import org.slf4j.LoggerFactory
import scala.util.Try
import scala.util.Success
import scala.util.Failure

package object deployment {

  type ApplicationDeploymentConfig = () => Config

  object DeploymentConfig {
    case class ConfigService(override val config: Config) extends com.azaptree.config.ConfigService
  }

  object applicationDeploymentConfigParams {
    case class ApplicationConfigRoot(value: String)

    case class ComponentConfigRoot(value: String)

    case class Namespace(value: String = "com.azaptree")
  }

  import applicationDeploymentConfigParams._

  val loadLocalApplicationDeploymentConfig: (ApplicationConfigRoot, ComponentConfigRoot, Namespace) => Try[Option[Config]] = (applicationConfigRoot, componentConfigRoot, namespace) => {
    val log = LoggerFactory.getLogger("com.azaptree.application.deployment.loadLocalApplicationDeploymentConfig")

    val defaultConfig = ConfigFactory.load().resolve()
    val compConfig = ConfigFactory.parseResourcesAnySyntax(applicationConfigRoot.value).resolve()
    val appConfig = ConfigFactory.parseResourcesAnySyntax(componentConfigRoot.value).resolve()

    val config = defaultConfig.withFallback(appConfig).withFallback(compConfig).resolve()
    val appInstanceId = applicationInstanceId(config, namespace)

    if (log.isDebugEnabled()) {
      log.debug("applicationConfigRoot = {}", componentConfigRoot)
      log.debug("compConfig:\n{}", toFormattedJson(compConfig))

      log.debug("componentConfigRoot = {}", componentConfigRoot)
      log.debug("appConfig:\n{}", toFormattedJson(appConfig))

      log.debug("appInstanceId = {}", appInstanceId)
      log.debug("config:\n{}", toFormattedJson(config))
    }

    val configService = DeploymentConfig.ConfigService(config)

    configService.applicationConfig(appInstanceId) match {
      case Success(config) =>
        Success(for {
          c <- config
        } yield {
          defaultConfig.withFallback(c)
        })
      case Failure(e) => Failure(e)
    }
  }

  val applicationInstanceId: (Config, Namespace) => ApplicationInstanceId = (config, namespace) => {
    val appInstanceId = s"${namespace.value}.app-instance-id"
    val id = for {
      group <- getString(config, s"$appInstanceId.group")
      name <- getString(config, s"$appInstanceId.name")
      version <- getString(config, s"$appInstanceId.version")
      instance <- getString(config, s"$appInstanceId.instance")
    } yield {
      val appId = ApplicationId(group = group, name = name)
      val appVersionId = ApplicationVersionId(appId = appId, version = version)
      ApplicationInstanceId(versionId = appVersionId, instance = instance)
    }

    id match {
      case None =>
        val formattedConfigJson = toFormattedJson(config)
        throw new IllegalStateException(s"$appInstanceId is not properly defined in the config :\n\n${formattedConfigJson}")
      case Some(x) => x
    }
  }

}