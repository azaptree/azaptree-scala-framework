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
import com.azaptree.application.model.ComponentVersionId
import org.slf4j.LoggerFactory

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
  protected val log = LoggerFactory.getLogger("com.azaptree.config.ComponentConfigs")

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
            Some(versionIds)
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
                val configInstances: Seq[Config] = versionConfig.getConfigList("configs")
                val compConfigInstanceIds = configInstances.foldLeft(List.empty[ComponentConfigInstanceId]) { (compConfigInstanceIds, configInstance) =>
                  ComponentConfigInstanceId(id, configInstance.getString("name")) :: compConfigInstanceIds
                }
                Some(compConfigInstanceIds)
            }
          } catch {
            case e: com.typesafe.config.ConfigException.Missing => None
            case e: Exception => throw e
          }
      }
    }
  }

  /**
   * if the configuration is invalid, then an Exception is returned
   */
  def componentConfigInstance(id: ComponentConfigInstanceId): Option[ComponentConfigInstance] = {
    def compDependencyRefs(configInstance: Config, compVersionConfig: Option[com.azaptree.config.ComponentVersionConfig]): Option[List[com.azaptree.config.ComponentConfigInstanceId]] = {
      try {
        val compDependencyRefs: Seq[Config] = configInstance.getConfigList("component-dependency-refs")
        val refs = compDependencyRefs.foldLeft(List.empty[ComponentConfigInstanceId]) { (refs, compDependencyRef) =>
          val compId = ComponentId(group = compDependencyRef.getString("group"), name = compDependencyRef.getString("name"))
          val version = compVersionConfig.get.compDependency(compId) match {
            case None => throw new IllegalStateException(s"Invalid component dependency [$compId] is defined for : $compVersionConfig")
            case Some(compVersionId) => compVersionId.version
          }
          val compConfigInstanceId = ComponentConfigInstanceId(
            versionId = ComponentVersionId(compId = compId, version = version),
            configInstanceName = compDependencyRef.getString("configName"))

          compConfigInstanceId :: refs
        }

        Some(refs)
      } catch {
        case e: com.typesafe.config.ConfigException.Missing => None
        case e: Exception => throw e
      }
    }

    if (compConfigs.isEmpty) {
      None
    } else {
      compConfigs.get(id.versionId.compId) match {
        case None => None
        case Some(compConfig) =>
          try {
            val versions: Seq[Config] = compConfig.getConfigList("versions")
            versions.find(_.getString("version") == id.versionId.version) match {
              case None => None
              case Some(versionConfig) =>
                val configInstances: Seq[Config] = versionConfig.getConfigList("configs")
                configInstances.find(_.getString("name") == id.configInstanceName) match {
                  case None => None
                  case Some(configInstance) =>
                    val compVersionConfig = componentVersionConfig(id.versionId)
                    val configInstanceConfig = getConfig(configInstance, "config")
                    Some(ComponentConfigInstance(id = id, config = configInstanceConfig, compDependencyRefs = compDependencyRefs(configInstance, compVersionConfig)))
                }
            }
          } catch {
            case e: com.typesafe.config.ConfigException.Missing => None
            case e: Exception => throw e
          }
      }
    }
  }

  def componentVersionConfig(versionId: ComponentVersionId): Option[ComponentVersionConfig] = {
    def validators(version: com.typesafe.config.Config): Option[Seq[com.azaptree.config.ConfigValidator]] = {
      try {
        val configValidatorClassNames: Seq[String] = version.getStringList("config-validators")
        val cl = getClass().getClassLoader()
        Some(configValidatorClassNames.map { className =>
          cl.loadClass(className).newInstance().asInstanceOf[ConfigValidator]
        })
      } catch {
        case e: com.typesafe.config.ConfigException.Missing => None
        case e: Exception => throw e
      }
    }

    def compDependencies(version: com.typesafe.config.Config) = {
      try {
        val configs: Seq[Config] = version.getConfigList("component-dependencies")
        Some(configs.foldLeft(List.empty[ComponentVersionId]) { (compDependencies, config) =>
          val group = config.getString("group")
          val name = config.getString("name")
          val version = config.getString("version")
          val compVersionId = ComponentVersionId(compId = ComponentId(group = group, name = name), version = version)
          compVersionId :: compDependencies
        })
      } catch {
        case e: com.typesafe.config.ConfigException.Missing => None
        case e: Exception => throw e
      }
    }

    if (compConfigs.isEmpty) {
      None
    } else {
      compConfigs.get(versionId.compId) match {
        case None => None
        case Some(compConfig) =>
          try {
            val versions: Seq[Config] = compConfig.getConfigList("versions")
            versions.find(_.getString("version") == versionId.version) match {
              case None =>
                log.debug("no matching version found for : {}", versionId)
                None
              case Some(version) =>
                Some(ComponentVersionConfig(
                  compVersionId = versionId,
                  configSchema = getConfig(version, "config-schema"),
                  validators = validators(version),
                  compDependencies = compDependencies(version)))
            }
          } catch {
            case e: com.typesafe.config.ConfigException.Missing =>
              if (log.isDebugEnabled()) log.debug(s"no matching version found for [$versionId] because of missing config path ", e)
              None
            case e: Exception => throw e
          }
      }
    }
  }

  /**
   * Will return an IllegalArgumentException if the compConfigInstanceId was not found
   */
  def validate(compConfigInstanceId: ComponentConfigInstanceId): Option[Exception] = {
    def checkThatAllDependenciesAreFullfilled(versionConfig: ComponentVersionConfig, compDependencyRefs: Option[Iterable[ComponentConfigInstanceId]]): Option[Exception] = {
      try {
        versionConfig.compDependencies.foreach { compDependencies =>
          compDependencies.foreach { compDependency =>
            compDependencyRefs match {
              case None => throw new IllegalStateException("There are no compDependencyRefs when there are some component dependcies on the ComponentVersionConfig")
              case Some(refs) =>
                refs.find(_.versionId == compDependency)
            }
          }

        }
        None
      } catch {
        case e: Exception => Some(e)
      }
    }

    try {
      componentConfigInstance(compConfigInstanceId) match {
        case None => Some(new IllegalArgumentException(s"ComponentConfigInstance not found for: $compConfigInstanceId"))
        case Some(instance) =>
          componentVersionConfig(compConfigInstanceId.versionId) match {
            case None => Some(new IllegalStateException(s"ComponentVersionConfig not found for: " + compConfigInstanceId.versionId))
            case Some(versionConfig) =>

              versionConfig.configSchema.foreach { configSchema =>
                instance.config.foreach { config =>
                  config.checkValid(configSchema)
                }
              }
              versionConfig.validators.foreach { validators =>
                validators.foreach { validator =>
                  validator.validate(instance.config.get)
                }
              }

              checkThatAllDependenciesAreFullfilled(versionConfig, instance.compDependencyRefs)
              None
          }

      }
    } catch {
      case e: Exception => Some(e)
    }

  }
}