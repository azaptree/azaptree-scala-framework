package com.azaptree.config

import com.azaptree.application.model.ComponentVersionId
import com.azaptree.application.model.ComponentDependency
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersion
import org.slf4j.LoggerFactory
import com.typesafe.config.Config
import com.azaptree.application.model.ComponentDependencyConfig
import com.azaptree.application.model.ComponentInstanceId

object ComponentConfigs {

  def getComponentDependencyRefs(versionConfig: Config, instanceConfig: Config): Option[Map[String, ComponentInstanceId]] = {
    def getComponentDependencyVersionId(versionConfig: Config, compId: ComponentId): ComponentVersionId = {
      getConfigList(versionConfig, "component-dependencies") match {
        case None => throw new IllegalStateException(s"No component dependencies were found : $versionConfig")
        case Some(compDependencyConfigs) =>
          compDependencyConfigs.find(c => c.getString("group") == compId.group && c.getString("name") == compId.name) match {
            case None => throw new IllegalStateException(s"No component dependency was found for: $compId")
            case Some(compDependencyConfig) => ComponentVersionId(compId = compId, version = compDependencyConfig.getString("version"))
          }
      }
    }

    getConfigList(instanceConfig, "component-dependency-refs") match {
      case None => None
      case Some(refs) =>
        Some(refs.foldLeft(Map.empty[String, ComponentInstanceId]) { (map, config) =>
          val compId = ComponentId(group = config.getString("group"), name = config.getString("name"))
          val versionId = getComponentDependencyVersionId(versionConfig, compId)
          val compInstanceId = ComponentInstanceId(versionId = versionId, instance = config.getString("config-ref-name"))
          val configName = getString(config, "config-name").getOrElse(compId.name)
          map + (configName -> compInstanceId)
        })
    }
  }

  def getComponentDependencies(versionConfig: Config): Option[Iterable[ComponentDependency]] = {
    getConfigList(versionConfig, "component-dependencies") match {
      case None => None
      case Some(configs) =>
        Some(configs.foldLeft(List.empty[ComponentDependency]) { (compDependencies, config) =>
          val group = config.getString("group")
          val name = config.getString("name")
          val version = config.getString("version")
          val compVersionId = ComponentVersionId(compId = ComponentId(group = group, name = name), version = version)

          getConfigList(config, "configs") match {
            case None => ComponentDependency(compVersionId) :: compDependencies
            case Some(configs) =>
              val compDependencyConfigs = configs.foldLeft(List.empty[ComponentDependencyConfig]) { (list, config) =>
                getConfigList(config, "attributes") match {
                  case None => ComponentDependencyConfig(config.getString("name")) :: list
                  case Some(attributes) =>
                    val attributeMap = attributes.foldLeft(Map.empty[String, String]) { (map, config) =>
                      map + (config.getString("name") -> config.getString("value"))
                    }
                    ComponentDependencyConfig(config.getString("name"), Some(attributeMap)) :: list
                }
              }

              ComponentDependency(compVersionId, Some(compDependencyConfigs)) :: compDependencies
          }

        })
    }
  }

  def getConfigInstanceAttributes(instanceConfig: Config): Option[Map[String, String]] = {
    getConfigList(instanceConfig, "attributes") match {
      case None => None
      case Some(attributes) =>
        Some(attributes.foldLeft(Map.empty[String, String]) { (map, config) =>
          map + (config.getString("name") -> config.getString("value"))
        })
    }
  }

}

trait ComponentConfigs extends ConfigLookup {
  protected val componentConfigsLog = LoggerFactory.getLogger("com.azaptree.config.ComponentConfigs")

  val compConfigs: Map[ComponentId, Config] = {
    val c: Config = config()

    getConfigList(c, "components") match {
      case None => throw new IllegalStateException("No components were found in the configuration")
      case Some(components) =>
        components.foldLeft(Map.empty[ComponentId, Config]) { (compConfigsMap, compConfig) =>
          val compId = ComponentId(group = compConfig.getString("group"), name = compConfig.getString("name"))
          compConfigsMap + (compId -> compConfig)
        }
    }
  }

  def componentIds(): Option[Iterable[ComponentId]] = {
    if (compConfigs.isEmpty) None else Some(compConfigs.keys)
  }

  def componentVersionIds(id: ComponentId): Option[Iterable[ComponentVersionId]] = {
    if (compConfigs.isEmpty) {
      None
    } else {
      compConfigs.get(id) match {
        case None => None
        case Some(compConfig) =>
          getConfigList(compConfig, "versions") match {
            case None => None
            case Some(versions) =>
              val versionIds = versions.foldLeft(List.empty[ComponentVersionId]) { (versionList, versionConfig) =>
                val versionId = ComponentVersionId(id, versionConfig.getString("version"))
                versionId :: versionList
              }
              Some(versionIds)
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
          getConfigList(compConfig, "versions") match {
            case None => None
            case Some(versions) =>
              versions.find(_.getString("version") == id.version) match {
                case None => None
                case Some(versionConfig) =>
                  ComponentConfigs.getComponentDependencies(versionConfig) match {
                    case None => Some(ComponentVersion(id))
                    case Some(componentDependencies) =>
                      if (componentDependencies.isEmpty) {
                        Some(ComponentVersion(id))
                      } else {
                        Some(ComponentVersion(id, Some(componentDependencies)))
                      }
                  }
              }
          }
      }
    }
  }

  def componentConfigInstanceIds(id: ComponentVersionId): Option[Iterable[ComponentInstanceId]] = {
    if (compConfigs.isEmpty) {
      None
    } else {
      compConfigs.get(id.compId) match {
        case None => None
        case Some(compConfig) =>
          getConfigList(compConfig, "versions") match {
            case None => None
            case Some(versions) =>
              versions.find(_.getString("version") == id.version) match {
                case None => None
                case Some(versionConfig) =>
                  getConfigList(versionConfig, "configs") match {
                    case None => None
                    case Some(configInstances) =>
                      val compInstanceIds = configInstances.foldLeft(List.empty[ComponentInstanceId]) { (compInstanceIds, configInstance) =>
                        ComponentInstanceId(id, configInstance.getString("name")) :: compInstanceIds
                      }
                      Some(compInstanceIds)
                  }
              }
          }
      }
    }
  }

  /**
   * if the configuration is invalid, then an Exception is returned
   */
  def componentConfigInstance(id: ComponentInstanceId): Option[ComponentConfigInstance] = {
    def compDependencyRefs(configInstance: Config, compVersionConfig: Option[com.azaptree.config.ComponentVersionConfig]): Option[List[ComponentInstanceId]] = {
      getConfigList(configInstance, "component-dependency-refs") match {
        case None => None
        case Some(compDependencyRefs) =>
          val refs = compDependencyRefs.foldLeft(List.empty[ComponentInstanceId]) { (refs, compDependencyRef) =>
            val compId = ComponentId(group = compDependencyRef.getString("group"), name = compDependencyRef.getString("name"))
            val version = compVersionConfig.get.compVersion.compDependency(compId) match {
              case None => throw new IllegalStateException(s"Invalid component dependency [$compId] is defined for : $compVersionConfig")
              case Some(compVersionId) => compVersionId.version
            }
            val compInstanceId = ComponentInstanceId(
              versionId = ComponentVersionId(compId = compId, version = version),
              instance = compDependencyRef.getString("config-ref-name"))

            compInstanceId :: refs
          }

          Some(refs)
      }
    }

    if (compConfigs.isEmpty) {
      None
    } else {
      compConfigs.get(id.versionId.compId) match {
        case None => None
        case Some(compConfig) =>
          getConfigList(compConfig, "versions") match {
            case None => None
            case Some(versions) =>
              versions.find(_.getString("version") == id.versionId.version) match {
                case None => None
                case Some(versionConfig) =>
                  getConfigList(versionConfig, "configs") match {
                    case None => None
                    case Some(configInstances) =>
                      configInstances.find(_.getString("name") == id.instance) match {
                        case None => None
                        case Some(instanceConfig) =>
                          val compVersionConfig = componentVersionConfig(id.versionId)
                          val configInstanceConfig = getConfig(instanceConfig, "config")
                          val compDependencyRefs = ComponentConfigs.getComponentDependencyRefs(versionConfig, instanceConfig)
                          val attributes = ComponentConfigs.getConfigInstanceAttributes(instanceConfig)
                          Some(ComponentConfigInstance(id = id, config = configInstanceConfig, compDependencyRefs = compDependencyRefs, attributes = attributes))
                      }
                  }
              }
          }

      }
    }
  }

  def componentVersionConfig(versionId: ComponentVersionId): Option[ComponentVersionConfig] = {
    def validators(version: com.typesafe.config.Config): Option[Seq[com.azaptree.config.ConfigValidator]] = {
      getStringList(version, "config-validators") match {
        case None => None
        case Some(configValidatorClassNames) =>
          val cl = getClass().getClassLoader()
          Some(configValidatorClassNames.map { className =>
            cl.loadClass(className).newInstance().asInstanceOf[ConfigValidator]
          })
      }
    }

    def compDependencies(version: com.typesafe.config.Config) = {
      getConfigList(version, "component-dependencies") match {
        case None => None
        case Some(configs) =>
          Some(configs.foldLeft(List.empty[ComponentVersionId]) { (compDependencies, config) =>
            val group = config.getString("group")
            val name = config.getString("name")
            val version = config.getString("version")
            val compVersionId = ComponentVersionId(compId = ComponentId(group = group, name = name), version = version)
            compVersionId :: compDependencies
          })
      }
    }

    if (compConfigs.isEmpty) {
      None
    } else {
      compConfigs.get(versionId.compId) match {
        case None => None
        case Some(compConfig) =>
          getConfigList(compConfig, "versions") match {
            case None => None
            case Some(versions) =>
              versions.find(_.getString("version") == versionId.version) match {
                case None =>
                  componentConfigsLog.debug("no matching version found for : {}", versionId)
                  None
                case Some(versionConfig) =>
                  Some(ComponentVersionConfig(
                    ComponentVersion(id = versionId, compDependencies = ComponentConfigs.getComponentDependencies(versionConfig)),
                    configSchema = getConfig(versionConfig, "config-schema"),
                    validators = validators(versionConfig)))
              }
          }
      }
    }
  }

  /**
   * Will return an IllegalArgumentException if the compConfigInstanceId was not found
   */
  def validate(compConfigInstanceId: ComponentInstanceId): Option[Exception] = {
    def checkThatAllDependenciesAreFullfilled(versionConfig: ComponentVersionConfig, compConfigInstance: ComponentConfigInstance) = {
      versionConfig.compVersion.compDependencies match {
        case None =>
          if (compConfigInstance.compDependencyRefs.isDefined) {
            throw new IllegalStateException(s"""There should not be any component dependency refs defined 
              | because there are no component dependencies defined on the component version config :
              | versionConfig : $versionConfig
              | compConfigInstance : $compConfigInstance""".stripMargin)
          }
        case Some(compDependencies) =>
          val compDependencyRefs = compConfigInstance.compDependencyRefs.get

          compDependencyRefs.foreach { ref =>
            if (componentConfigInstance(ref._2).isEmpty) {
              throw new IllegalStateException(s"""ComponentConfigInstance not foud for : %s
                  |
                  | ComponentVersionConfig : $versionConfig
                  |
                  | ComponentConfigInstance : $compConfigInstance""".stripMargin.format(ref._2));
            }
          }

          compDependencies.foreach { compDependency =>

            compDependency.configs match {
              case None =>
                if (compDependencyRefs.values.find(_.versionId == compDependency.compVersionId).isEmpty) {
                  throw new IllegalStateException(s"""ComponentDependency not satisfied: 
                      |
                      | $compDependency 
                      |
                      | versionConfig : $versionConfig
                      |
                      | compConfigInstance : $compConfigInstance""".stripMargin)
                }
              case Some(compDependencyConfigs) =>
                compDependencyConfigs.foreach { compDependencyConfig =>
                  compDependencyRefs.get(compDependencyConfig.name) match {
                    case None => throw new IllegalStateException(s"""ComponentDependency not satisfied: 
                      |
                      | $compDependencyConfig 
                      |
                      | versionConfig : $versionConfig
                      |
                      | compConfigInstance : $compConfigInstance""".stripMargin)
                    case Some(compDependencyRefId) => componentConfigInstance(compDependencyRefId) match {
                      case None => throw new IllegalStateException("ComponentConfigInstance does not exist: $compDependencyRefId")
                      case Some(compRefConfigInstance) =>
                        compDependencyConfig.attributes.foreach { attributes =>
                          compRefConfigInstance.attributes match {
                            case None => throw new IllegalStateException("""The following attributes are required : %s
                          	        | versionConfig : %s
                          	        | compConfigInstance: %s""".stripMargin.format(attributes.mkString(","), versionConfig, compConfigInstance))
                            case Some(refAttributes) =>
                              attributes.forall { keyValue =>
                                refAttributes.get(keyValue._1) match {
                                  case None => false
                                  case Some(value) => keyValue._2 == value
                                }
                              }
                          }
                        }
                    }
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

              versionConfig.configSchema match {
                case None =>
                  if (instance.config.isDefined) {
                    throw new IllegalStateException("There is a ComponenConfigInstace.config defined even though there is no config schema defined on the ComponentVersionConfig");
                  }

                case Some(configSchema) =>
                  instance.config match {
                    case None => throw new IllegalStateException("The ComponentConfigInstance requires a config because the ComponentVersionConfig has a configSchema defined")
                    case Some(config) => config.checkValid(configSchema)
                  }
              }

              versionConfig.validators.foreach(_.foreach(_.validate(instance.config.get)))
              checkThatAllDependenciesAreFullfilled(versionConfig, instance)
              None
          }
      }
    } catch {
      case e: Exception => Some(e)
    }

  }
}