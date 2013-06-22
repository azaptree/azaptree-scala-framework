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

  val compConfigs: Map[ComponentId, Config] = {
    val c: Config = config()

    val components: Seq[Config] = {
      try {
        c.getConfigList("components")
      } catch {
        case e: com.typesafe.config.ConfigException.Missing => Nil
        case e: Exception => throw e
      }
    }

    val emptyComponentConfigsMap: Map[ComponentId, Config] = Map.empty[ComponentId, Config]
    components.foldLeft(emptyComponentConfigsMap) { (compConfigsMap, compConfig) =>
      val compId = ComponentId(group = compConfig.getString("group"), name = compConfig.getString("name"))
      compConfigsMap + (compId -> compConfig)
    }
  }

  def componentIds(): Option[Iterable[ComponentId]] = {
    if (compConfigs.isEmpty) None else Some(compConfigs.keys)
  }

  def componentVersions(id: ComponentId): Option[Iterable[ComponentVersionId]] = {
    if (compConfigs.isEmpty) {
      None
    } else {
      compConfigs.get(id) match {
        case None => None
        case Some(compConfig) =>
          try {
            val versions: Seq[Config] = compConfig.getConfigList("versions")
            val versionIds = versions.foldLeft(List.empty[ComponentVersionId]) { (versionList, versionConfig) =>
              val versionId = ComponentVersionId(id, versionConfig.getString("version"))
              versionId :: versionList
            }
            if (versionIds.isEmpty) None else Some(versionIds)
          } catch {
            case e: com.typesafe.config.ConfigException.Missing => None
            case e: Exception => throw e
          }
      }
    }
  }

  def componentVersion(id: ComponentVersionId): Option[ComponentVersion] = {
    if (compConfigs.isEmpty) {
      None
    } else {
      compConfigs.get(id.compId) match {
        case None => None
        case Some(compConfig) =>
          try {
            val versions: Seq[Config] = compConfig.getConfigList("versions")
            versions.find(_.getString("version") == id.version) match {
              case None => None
              case Some(versionConfig) =>
                try {
                  val compDependencies: Seq[Config] = versionConfig.getConfigList("component-dependencies")
                  val compVersionIds = compDependencies.foldLeft(List.empty[ComponentVersionId]) { (compVersionIds, compDependencyConfig) =>
                    val compId = ComponentId(group = compDependencyConfig.getString("group"), name = compDependencyConfig.getString("name"))
                    ComponentVersionId(compId, compDependencyConfig.getString("version")) :: compVersionIds
                  }

                  val compVersions = compVersionIds.map(id => componentVersion(id)).filter(_.isDefined).map(_.get).toIterable
                  Some(ComponentVersion(id, Some(compVersions)))
                } catch {
                  case e: com.typesafe.config.ConfigException.Missing => Some(ComponentVersion(id))
                  case e: Exception => throw e
                }
            }
          } catch {
            case e: com.typesafe.config.ConfigException.Missing => None
            case e: Exception => throw e
          }
      }
    }
  }

  def componentConfigInstanceIds(id: ComponentVersionId): Option[Iterable[ComponentConfigInstanceId]] = {
    throw new OperationNotSupportedException
  }

  def componentConfigInstance(id: ComponentVersionId): Option[ComponentConfigInstance] = {
    throw new OperationNotSupportedException
  }
}