package com.azaptree.config

import com.azaptree.application.model.ApplicationVersion
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ComponentVersionId
import com.azaptree.application.model.ApplicationInstanceId
import com.azaptree.application.model.ComponentInstanceId

case class ApplicationVersionConfig(
  appVersion: ApplicationVersion,
  configSchema: Option[com.typesafe.config.Config] = None,
  validators: Option[Iterable[ConfigValidator]] = None)

/**
 * attributes serve as meta-data that are searchable. For example, find all application config instances with attribute env=DEV
 *
 */
case class ApplicationConfigInstance(
  id: ApplicationInstanceId,
  config: Option[com.typesafe.config.Config],
  compDependencyRefs: Option[Map[String /* logical name used by the component / app to lookup the config */ , ComponentInstanceId]] = None,
  attributes: Option[Map[String, String]] = None)
