package com.azaptree.config

import com.azaptree.application.model.ComponentVersion
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersionId
import com.typesafe.config.Config

case class ComponentVersionConfig(
  compVersion: ComponentVersion,
  configSchema: Option[com.typesafe.config.Config] = None,
  validators: Option[Iterable[ConfigValidator]] = None)

/**
 * <pre>
 * - attributes serve as meta-data that can be used to match up dependencies. For example, a datasource can have a "database" attribute.
 *   Another component depends on a datasource with attribute database=users - not just any datasource.
 *   In addition, the meta-data is searchable. For example, find datasource component config instances where database=users and env=DEV
 *
 * - compDependencyRefs - the map key refers to the logical name used by the component / app to lookup the config
 * </pre>
 *
 */
case class ComponentConfigInstance(
  id: ComponentConfigInstanceId,
  config: Option[com.typesafe.config.Config] = None,
  compDependencyRefs: Option[Map[String, ComponentConfigInstanceId]] = None,
  attributes: Option[Map[String, String]] = None)

case class ComponentConfigInstanceId(versionId: ComponentVersionId, configInstanceName: String)

trait ConfigValidator {
  def validate(config: Config): Option[Exception]
}