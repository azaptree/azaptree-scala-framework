package com.azaptree.config

import com.azaptree.application.model.ApplicationVersion

case class ApplicationConfig(
  appVersion: ApplicationVersion,
  configSchema: Option[com.typesafe.config.Config] = None,
  validators: Option[ConfigValidator] = None)

case class ApplicationConfigInstance(
  appConfig: ApplicationConfig,
  name: String,
  config: Option[com.typesafe.config.Config],
  compDependencies: Option[Iterable[ComponentDependency]] = None)