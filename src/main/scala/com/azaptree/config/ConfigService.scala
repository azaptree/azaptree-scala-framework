package com.azaptree.config

import com.azaptree.application.model.ApplicationId
import com.azaptree.application.model.ApplicationVersion
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersion
import com.azaptree.application.model.ComponentVersionId
import com.typesafe.config.Config
import com.azaptree.application.model.ComponentVersionId
import com.azaptree.application.model.ApplicationVersionId
import scala.collection.JavaConversions._
import com.typesafe.config.ConfigValue
import javax.naming.OperationNotSupportedException

trait ConfigService extends ApplicationConfigs with ComponentConfigs {
  def applicationConfig(id: ApplicationConfigInstanceId): Config

  def componentConfig(id: ComponentVersionId): Config
}

trait ConfigLookup {
  def config(): Config
}

trait ApplicationConfigs extends ConfigLookup {
  def applicationIds(): Option[Iterable[ApplicationId]]

  def applicationVersions(id: ApplicationId): Option[Iterable[ApplicationVersionId]]

  def applicationVersion(id: ApplicationVersionId): Option[ApplicationVersion]

  def applicationConfigInstanceNames(id: ApplicationVersionId): Option[Iterable[ApplicationConfigInstanceId]]

  def applicationConfigInstance(id: ApplicationConfigInstanceId): Option[ApplicationConfigInstance]
}

trait ComponentConfigs extends ConfigLookup {
  def componentIds(): Option[Iterable[ComponentId]] = {
    val c: Config = config()
    val components: Seq[Config] = c.getConfigList("components")

    val emptyComponentIdsList: List[ComponentId] = Nil
    val componentIds: List[ComponentId] = components.foldLeft(emptyComponentIdsList) { (compIds, compConfig) =>
      val compId = ComponentId(group = compConfig.getString("group"), name = compConfig.getString("name"))
      compId :: compIds
    }

    if (componentIds.isEmpty) None else Some(componentIds)
  }

  def componentVersions(id: ComponentId): Option[Iterable[ComponentVersionId]] = {
    throw new OperationNotSupportedException
  }

  def componentVersion(id: ComponentVersionId): Option[ComponentVersion] = {
    throw new OperationNotSupportedException
  }

  def componentConfigInstanceIds(id: ComponentVersionId): Option[Iterable[ComponentConfigInstanceId]] = {
    throw new OperationNotSupportedException
  }

  def componentConfigInstance(id: ComponentVersionId): Option[ComponentConfigInstance] = {
    throw new OperationNotSupportedException
  }
}