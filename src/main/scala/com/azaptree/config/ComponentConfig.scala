package com.azaptree.config

import com.azaptree.application.model.ComponentVersion
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersionId
import com.typesafe.config.Config

case class ComponentVersionConfig(
  compVersion: ComponentVersion,
  configSchema: Option[com.typesafe.config.Config] = None,
  validators: Option[Iterable[ConfigValidator]] = None)

case class ComponentConfigInstance(
  id: ComponentConfigInstanceId,
  config: Option[com.typesafe.config.Config] = None,
  compDependencyRefs: Option[Iterable[ComponentConfigInstanceId]] = None)

case class ComponentConfigInstanceId(versionId: ComponentVersionId, configInstanceName: String)

trait ConfigValidator {
  def validate(config: Config): Option[Exception]
}