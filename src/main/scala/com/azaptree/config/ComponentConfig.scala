package com.azaptree.config

import com.azaptree.application.model.ComponentVersion

case class ComponentConfig(
  compVersion: ComponentVersion,
  configSchema: Option[com.typesafe.config.Config] = None,
  validators: Option[ConfigValidator] = None)

case class ComponentConfigInstance(
  compConfig: ComponentConfig,
  name: String,
  config: com.typesafe.config.Config,
  compDependencies: Option[Iterable[ComponentDependency]] = None)

case class ComponentDependency(
  compVersion: ComponentVersion,
  compConfigInstanceName: String,
  config: Option[com.typesafe.config.Config] = None)