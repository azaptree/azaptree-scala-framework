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
  protected val applicationConfigsLog = LoggerFactory.getLogger("com.azaptree.config.ApplicationConfigs")

  val appConfigs: Map[ApplicationId, Config] = {
    val c = config()

    val apps: Seq[Config] = try {
      c.getConfigList("applications")
    } catch {
      case e: com.typesafe.config.ConfigException.Missing => Nil
      case e: Exception => throw e
    }

    apps.foldLeft(Map.empty[ApplicationId, Config]) { (map, config) =>
      map + (ApplicationId(group = config.getString("group"), name = config.getString("name")) -> config)
    }
  }

  def applicationIds(): Option[Iterable[ApplicationId]] = {
    if (appConfigs.isEmpty) None else Some(appConfigs.keys)
  }

  def applicationVersions(id: ApplicationId): Option[Iterable[ApplicationVersionId]] = {
    throw new UnsupportedOperationException
  }

  def applicationVersion(id: ApplicationVersionId): Option[ApplicationVersion] = {
    throw new UnsupportedOperationException
  }

  def applicationConfigInstanceNames(id: ApplicationVersionId): Option[Iterable[ApplicationConfigInstanceId]] = {
    throw new UnsupportedOperationException
  }

  def applicationConfigInstance(id: ApplicationConfigInstanceId): Option[ApplicationConfigInstance] = {
    throw new UnsupportedOperationException
  }
}

trait ComponentConfigs extends ConfigLookup {
  protected val componentConfigsLog = LoggerFactory.getLogger("com.azaptree.config.ComponentConfigs")

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

    components.foldLeft(Map.empty[ComponentId, Config]) { (compConfigsMap, compConfig) =>
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
                componentConfigsLog.debug("no matching version found for : {}", versionId)
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
              if (componentConfigsLog.isDebugEnabled()) componentConfigsLog.debug(s"no matching version found for [$versionId] because of missing config path ", e)
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
    def checkThatAllDependenciesAreFullfilled(versionConfig: ComponentVersionConfig, compDependencyRefs: Option[Iterable[ComponentConfigInstanceId]]) = {
      versionConfig.compDependencies match {
        case None => if (compDependencyRefs.isDefined) throw new IllegalStateException("""There should not be any component dependency refs defined 
              | because there are no component dependencies defined on the component version config""".stripMargin)
        case Some(compDependencies) =>
          if (componentConfigsLog.isDebugEnabled()) {
            componentConfigsLog.debug("compDependencyRefs.isEmpty || compDependencyRefs.get.size != compDependencies.size = " + (compDependencyRefs.isEmpty || compDependencyRefs.get.size != compDependencies.size))
            componentConfigsLog.debug("compDependencyRefs.isEmpty = " + compDependencyRefs.isEmpty)
            componentConfigsLog.debug("(compDependencyRefs.get.size, compDependencies.size) = (%d,%d) ".format(compDependencyRefs.get.size, compDependencies.size))
          }

          if (compDependencyRefs.isEmpty || compDependencyRefs.get.size != compDependencies.size) {
            throw new IllegalStateException("The number of component dependency refs defined does not match the number of component dependencies defined in the component version config")
          }

          val refs = compDependencyRefs.get

          compDependencies.foreach { compDependency =>
            refs.find(_.versionId == compDependency) match {
              case None => throw new IllegalStateException(s"There is no component dependency ref defined for : $compDependency")
              case Some(compDependencyRef) =>
                componentConfigInstance(compDependencyRef) match {
                  case None => throw new IllegalStateException(s"There is no such ComponentConfigInstance for: $compDependencyRef")
                  case Some(compConfigInstance) => validate(compDependencyRef) match {
                    case Some(e) => throw e
                    case None => // is valid
                  }
                }
            }

          }
      }
    }

    try {
      componentConfigInstance(compConfigInstanceId) match {
        case None => Some(new IllegalArgumentException(s"ComponentConfigInstance not found for: $compConfigInstanceId"))
        case Some(instance) =>
          componentConfigsLog.debug("validating instance: {}", instance)

          componentVersionConfig(compConfigInstanceId.versionId) match {
            case None => Some(new IllegalStateException(s"ComponentVersionConfig not found for: " + compConfigInstanceId.versionId))
            case Some(versionConfig) =>

              versionConfig.configSchema.foreach { configSchema =>
                instance.config.foreach(_.checkValid(configSchema))
              }
              versionConfig.validators.foreach(_.foreach(_.validate(instance.config.get)))
              checkThatAllDependenciesAreFullfilled(versionConfig, instance.compDependencyRefs)
              None
          }
      }
    } catch {
      case e: Exception => Some(e)
    }

  }
}