package com.azaptree.config

import com.azaptree.application.model.ComponentVersion
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersionId
import com.typesafe.config.Config

case class ComponentVersionConfig(
    compVersionId: ComponentVersionId,
    configSchema: Option[com.typesafe.config.Config] = None,
    validators: Option[Iterable[ConfigValidator]] = None,
    compDependencies: Option[Iterable[ComponentVersionId]] = None) {

  def compDependency(compId: ComponentId): Option[ComponentVersionId] = {
    compDependencies match {
      case None => None
      case Some(versionIds) => versionIds.find(_.compId == compId)
    }
  }
}

case class ComponentConfigInstance(
  id: ComponentConfigInstanceId,
  config: Option[com.typesafe.config.Config] = None,
  compDependencyRefs: Option[Iterable[ComponentConfigInstanceId]] = None)

case class ComponentConfigInstanceId(versionId: ComponentVersionId, configInstanceName: String)

trait ConfigValidator {
  def validate(config: Config): Option[Exception]
}