package com.azaptree.application

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.azaptree.config._
import com.azaptree.application.model._

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

  val loadLocalApplicationDeploymentConfig: (ApplicationConfigRoot, ComponentConfigRoot, Namespace) => Either[Exception, Option[Config]] = (applicationConfigRoot, componentConfigRoot, namespace) => {
    val defaultConfig = ConfigFactory.load().resolve()
    val compConfig = ConfigFactory.parseResourcesAnySyntax(applicationConfigRoot.value).resolve()
    val appConfig = ConfigFactory.parseResourcesAnySyntax(componentConfigRoot.value).resolve()

    val config = defaultConfig.withFallback(appConfig).withFallback(compConfig).resolve()
    val appInstanceId = applicationInstanceId(config, namespace)

    val configService = DeploymentConfig.ConfigService(config)
    configService.applicationConfig(appInstanceId)
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
      case None => throw new IllegalStateException(s"$appInstanceId is not properly defined in the config")
      case Some(x) => x
    }
  }

}