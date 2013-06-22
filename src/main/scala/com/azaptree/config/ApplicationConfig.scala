package com.azaptree.config

import com.azaptree.application.model.ApplicationVersion
import com.azaptree.application.model.ApplicationVersionId

case class ApplicationConfig(
  appVersionId: ApplicationVersionId,
  configSchema: Option[com.typesafe.config.Config] = None,
  validators: Option[ConfigValidator] = None)

case class ApplicationConfigInstance(
    appConfig: ApplicationConfig,
    name: String,
    config: Option[com.typesafe.config.Config],
    compDependencies: Option[Iterable[ComponentDependency]] = None) {
  val id = ApplicationConfigInstanceId(versionId = appConfig.appVersionId, configInstanceName = name)
}

case class ApplicationConfigInstanceId(versionId: ApplicationVersionId, configInstanceName: String)