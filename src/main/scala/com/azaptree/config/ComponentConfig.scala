package com.azaptree.config

import com.azaptree.application.model.ComponentVersion
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersionId

case class ComponentConfig(
  compVersion: ComponentVersion,
  configSchema: Option[com.typesafe.config.Config] = None,
  validators: Option[ConfigValidator] = None)

case class ComponentConfigInstance(
    compConfig: ComponentConfig,
    name: String,
    config: com.typesafe.config.Config,
    compDependencies: Option[Iterable[ComponentDependency]] = None) {
  val id = ComponentConfigInstanceId(versionId = compConfig.compVersion.id, configInstanceName = name)
}

case class ComponentDependency(
  compVersion: ComponentVersion,
  compConfigInstanceName: String,
  config: Option[com.typesafe.config.Config] = None)

case class ComponentConfigInstanceId(versionId: ComponentVersionId, configInstanceName: String)