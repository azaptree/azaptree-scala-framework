package com.azaptree.config

import com.azaptree.application.model.ApplicationId
import com.azaptree.application.model.ApplicationVersion
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersion
import com.azaptree.application.model.ComponentVersionId
import com.typesafe.config.Config

trait ConfigService extends ApplicationConfigs with ComponentConfigs {
  def applicationConfig(versionId: ApplicationVersionId, configInstanceName: String): Config

  def componentConfig(versionId: ComponentVersionId, configInstanceName: String): Config
}

trait ConfigLookup {
  def config(): Config
}

trait ApplicationConfigs extends ConfigLookup {
  def applicationIds(): Option[Iterable[ApplicationId]]

  def applicationVersions(name: String): Option[Iterable[String]]

  def applicationVersion(name: String, version: String): Option[ApplicationVersion]

  def applicationConfigInstanceNames(name: String, version: String): Option[Iterable[String]]

  def applicationConfigInstance(name: String, version: String, instanceName: String): Option[ApplicationConfigInstance]
}

trait ComponentConfigs extends ConfigLookup {
  def componentIds(): ComponentId

  def componentVersions(name: String): Option[Iterable[String]]

  def componentVersion(name: String, version: String): Option[ComponentVersion]

  def componentConfigInstanceNames(name: String, version: String): Option[Iterable[String]]

  def componentConfigInstance(name: String, version: String, instanceName: String): Option[ComponentConfigInstance]
}