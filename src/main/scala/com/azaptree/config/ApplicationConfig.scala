package com.azaptree.config

import com.azaptree.application.model.ApplicationVersion
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ComponentVersionId

case class ApplicationVersionConfig(
  appVersionId: ApplicationVersionId,
  configSchema: Option[com.typesafe.config.Config] = None,
  validators: Option[Iterable[ConfigValidator]] = None,
  compDependencies: Option[Iterable[ComponentVersionId]] = None)

case class ApplicationConfigInstance(
  id: ApplicationConfigInstanceId,
  config: Option[com.typesafe.config.Config],
  compDependencyRefs: Option[Iterable[ComponentConfigInstanceId]] = None)

case class ApplicationConfigInstanceId(versionId: ApplicationVersionId, configInstanceName: String)