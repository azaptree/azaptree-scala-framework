package com.azaptree.config

import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory
import com.azaptree.application.model.ApplicationId
import com.azaptree.application.model.ApplicationVersion
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersion
import com.azaptree.application.model.ComponentVersionId
import com.azaptree.application.model.ComponentVersionId
import com.azaptree.application.model.ComponentVersionId
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.azaptree.application.model.ComponentDependency
import com.azaptree.application.model.ComponentDependencyConfig

trait ConfigService extends ApplicationConfigs {

  /**
   * The configuration instance is validated before returning
   */
  def applicationConfig(id: ApplicationConfigInstanceId): Either[Exception, Option[Config]] = {
    def configWithDependencies(config: Config, configInstance: ApplicationConfigInstance): Either[Exception, Option[Config]] = {
      configInstance.compDependencyRefs match {
        case None => Right(Some(config))
        case Some(compDependencyRefs) =>
          try {
            Right(Some(compDependencyRefs.foldLeft(config) { (c, compDependencyRef) =>
              componentConfig(compDependencyRef._2) match {
                case Left(e) => throw e
                case Right(None) => c
                case Right(Some(compConfig)) =>
                  //TODO: wrap config within the compDependencyRef name
                  c.withFallback(compConfig)
              }
            }))
          } catch {
            case e: Exception => Left(e)
          }
      }
    }

    validate(id) match {
      case Some(e) => Left(e)
      case None =>
        applicationConfigInstance(id) match {
          case None => Right(None)
          case Some(appConfigInstance) =>
            appConfigInstance.config match {
              case None => configWithDependencies(ConfigFactory.empty(), appConfigInstance)
              case Some(appConfig) => configWithDependencies(appConfig, appConfigInstance)
            }
        }
    }
  }

  /**
   * The configuration instance is validated before returning
   */
  def componentConfig(id: ComponentConfigInstanceId): Either[Exception, Option[Config]] = {
    def configWithDependencies(config: Config, compConfigInstance: ComponentConfigInstance): Either[Exception, Option[Config]] = {
      compConfigInstance.compDependencyRefs match {
        case None => Right(Some(config))
        case Some(compDependencyRefs) =>
          try {
            Right(Some(compDependencyRefs.foldLeft(config) { (config, compDependencyRef) =>
              componentConfig(compDependencyRef._2) match {
                case Right(None) => config
                case Right(Some(compConfig)) =>
                  //TODO: wrap config within the compDependencyRef name
                  config.withFallback(compConfig)
                case Left(e) => throw e
              }
            }))
          } catch {
            case e: Exception => Left(e)
          }
      }
    }

    validate(id) match {
      case None =>
        componentConfigInstance(id) match {
          case None => Right(None)
          case Some(compConfigInstance) =>
            compConfigInstance.config match {
              case None => configWithDependencies(ConfigFactory.empty(), compConfigInstance)
              case Some(config) => configWithDependencies(config, compConfigInstance)
            }
        }
      case Some(e) => Left(e)
    }

  }
}

trait ConfigLookup {
  def config(): Config
}

trait ApplicationConfigs extends ComponentConfigs {
  protected val applicationConfigsLog = LoggerFactory.getLogger("com.azaptree.config.ApplicationConfigs")

  val appConfigs: Map[ApplicationId, Config] = {
    val c = config()

    getConfigList(c, "applications") match {
      case None => throw new IllegalStateException("There are no applications in the configuration")
      case Some(apps) =>
        apps.foldLeft(Map.empty[ApplicationId, Config]) { (map, config) =>
          map + (ApplicationId(group = config.getString("group"), name = config.getString("name")) -> config)
        }
    }
  }

  def applicationIds(): Option[Iterable[ApplicationId]] = {
    if (appConfigs.isEmpty) None else Some(appConfigs.keys)
  }

  def applicationVersionIds(id: ApplicationId): Option[Iterable[ApplicationVersionId]] = {
    appConfigs.get(id) match {
      case None => None
      case Some(appConfig) =>
        getConfigList(appConfig, "versions") match {
          case None => None
          case Some(versionConfigs) =>
            val appVersionIds = versionConfigs.foldLeft(List.empty[ApplicationVersionId]) { (list, config) =>
              ApplicationVersionId(appId = id, version = config.getString("version")) :: list
            }

            if (appVersionIds.isEmpty) None else Some(appVersionIds)
        }
    }
  }

  def applicationVersion(id: ApplicationVersionId): Option[ApplicationVersion] = {
    appConfigs.get(id.appId) match {
      case None => None
      case Some(appConfig) =>
        getConfigList(appConfig, "versions") match {
          case None => None
          case Some(versionConfigs) =>
            versionConfigs.find(_.getString("version") == id.version) match {
              case None => None
              case Some(versionConfig) =>
                getConfigList(versionConfig, "component-dependencies") match {
                  case None => Some(ApplicationVersion(id))
                  case Some(compDependencyConfigs) =>
                    val compDependencies = compDependencyConfigs.foldLeft(List.empty[ComponentDependency]) { (list, config) =>
                      val compId = ComponentId(group = config.getString("group"), name = config.getString("name"))
                      val compVersionId = ComponentVersionId(compId = compId, version = config.getString("version"))
                      val componentDependencyConfigs = for {
                        componentDependencyConfigs <- getConfigList(config, "configs")
                      } yield {
                        componentDependencyConfigs.foldLeft(List.empty[ComponentDependencyConfig]) { (list, config) =>
                          ComponentDependencyConfig(name = config.getString("name"), attributes = {
                            for {
                              attributes <- getConfigList(config, "attributes")
                            } yield {
                              attributes.foldLeft(Map.empty[String, String]) { (map, config) =>
                                map + (config.getString("name") -> config.getString("value"))
                              }
                            }
                          }) :: list
                        }
                      }

                      ComponentDependency(compVersionId = compVersionId, configs = componentDependencyConfigs) :: list

                    }
                    Some(ApplicationVersion(id, Some(compDependencies)))
                }
            }
        }
    }
  }

  def applicationConfigInstanceIds(id: ApplicationVersionId): Option[Iterable[ApplicationConfigInstanceId]] = {
    appConfigs.get(id.appId) match {
      case None => None
      case Some(appConfig) =>
        getConfigList(appConfig, "versions") match {
          case None => None
          case Some(versionConfigs) =>
            versionConfigs.find(_.getString("version") == id.version) match {
              case None => None
              case Some(versionConfig) =>
                getConfigList(versionConfig, "configs") match {
                  case None => None
                  case Some(instanceConfigs) =>
                    val appConfigInstanceIds = instanceConfigs.foldLeft(List.empty[ApplicationConfigInstanceId]) { (list, config) =>
                      ApplicationConfigInstanceId(id, config.getString("name")) :: list
                    }
                    Some(appConfigInstanceIds)
                }
            }
        }
    }
  }

  def applicationConfigInstance(id: ApplicationConfigInstanceId): Option[ApplicationConfigInstance] = {

    appConfigs.get(id.versionId.appId) match {
      case None => None
      case Some(appConfig) =>
        getConfigList(appConfig, "versions") match {
          case None => None
          case Some(versionConfigs) =>
            versionConfigs.find(_.getString("version") == id.versionId.version) match {
              case None => None
              case Some(versionConfig) =>
                getConfigList(versionConfig, "configs") match {
                  case None => None
                  case Some(instanceConfigs) =>
                    instanceConfigs.find(_.getString("name") == id.configInstanceName) match {
                      case None => None
                      case Some(instanceConfig) =>
                        Some(ApplicationConfigInstance(
                          id = id,
                          config = getConfig(instanceConfig, "config"),
                          compDependencyRefs = ComponentConfigs.getComponentDependencyRefs(versionConfig, instanceConfig),
                          attributes = ComponentConfigs.getConfigInstanceAttributes(instanceConfig)))
                    }
                }
            }
        }
    }
  }

  def applicationVersionConfig(versionId: ApplicationVersionId): Option[ApplicationVersionConfig] = {
    def getValidators(versionConfig: Config): Option[Iterable[ConfigValidator]] = {
      getStringList(versionConfig, "config-validators") match {
        case None => None
        case Some(configValidatorClassNames) =>
          val cl = getClass().getClassLoader()
          Some(configValidatorClassNames.map { className =>
            cl.loadClass(className).newInstance().asInstanceOf[ConfigValidator]
          })
      }
    }

    appConfigs.get(versionId.appId) match {
      case None => None
      case Some(appConfig) =>
        getConfigList(appConfig, "versions") match {
          case None =>
            applicationConfigsLog.warn("no versions found for : {}", versionId.appId)
            None
          case Some(versionConfigs) =>
            versionConfigs.find(_.getString("version") == versionId.version) match {
              case None =>
                applicationConfigsLog.debug("no version config found for : {}", versionId)
                None
              case Some(versionConfig) =>
                val appVersionConfig = ApplicationVersionConfig(
                  ApplicationVersion(id = versionId, dependencies = ComponentConfigs.getComponentDependencies(versionConfig)),
                  configSchema = getConfig(versionConfig, "config-schema"),
                  validators = getValidators(versionConfig))
                Some(appVersionConfig)
            }
        }
    }
  }

  def validate(id: ApplicationConfigInstanceId): Option[Exception] = {

    def validateConfigSchema(appVersionConfig: ApplicationVersionConfig, appConfigInstance: ApplicationConfigInstance) = {
      appConfigInstance.config match {
        case None => if (appVersionConfig.configSchema.isDefined) throw new IllegalStateException("The application config instance requires a Config to be defined")
        case Some(config) =>
          appVersionConfig.configSchema match {
            case None => throw new IllegalStateException("The application config instance has a Config defined even though there is no Config schema defined for the application version")
            case Some(configSchema) =>
              config.checkValid(configSchema)
          }
      }
    }

    def runValidators(appVersionConfig: ApplicationVersionConfig, appConfigInstance: ApplicationConfigInstance) = {
      for {
        validators <- appVersionConfig.validators
      } yield {
        validators.foreach { validator =>
          validator.validate(appConfigInstance.config.get) match {
            case None =>
            case Some(e) => throw e
          }
        }
      }
    }

    def validateComponentDependencies(appVersionConfig: ApplicationVersionConfig, appConfigInstance: ApplicationConfigInstance) = {
      appVersionConfig.appVersion.dependencies match {
        case None =>
          if (appConfigInstance.compDependencyRefs.isDefined) {
            throw new IllegalStateException("""There should not be any component dependency refs defined 
              | because there are no component dependencies defined on the application version config""".stripMargin)
          }
        case Some(compDependencies) =>
          val refs = appConfigInstance.compDependencyRefs.get

          refs.foreach { ref =>
            if (componentConfigInstance(ref._2).isEmpty) {
              throw new IllegalStateException("ComponentConfigInstance not foud for : " + ref._2);
            }
          }

          //validate that the component dependency configs are defined with the matching attributes
          compDependencies.foreach { compDependency =>
            compDependency.configs match {
              case None =>
                if (refs.values.find(_.versionId == compDependency.compVersionId).isEmpty) {
                  throw new IllegalStateException(s"""ComponentDependency is not satisfied :
                      | compDependency : $compDependency
                      |
                      | appVersionConfig : $appVersionConfig
                      |
                      | appConfigInstance : $appConfigInstance""".stripMargin)
                }
              case Some(compDependencyConfigs) =>
                compDependencyConfigs.foreach { compDependencyConfig =>
                  refs.get(compDependencyConfig.name) match {
                    case None =>
                    case Some(compRefConfigInstanceId) =>
                      compDependencyConfig.attributes.foreach { attributes =>
                        componentConfigInstance(compRefConfigInstanceId) match {
                          case None => throw new IllegalStateException("""ComponentConfigInstance was not found for: %s"
                            | appVersionConfig : %s
                            | appConfigInstance : %s""".stripMargin.format(compRefConfigInstanceId, appVersionConfig, appConfigInstance))
                          case Some(compRefConfigInstance) => validate(compRefConfigInstanceId) match {
                            case Some(exception) => throw exception
                            case None =>
                              compDependencyConfig.attributes.foreach { attributes =>
                                compRefConfigInstance.attributes match {
                                  case None => throw new IllegalStateException("""The following attributes are required : %s
                          	        | appVersionConfig : %s
                          	        | appConfigInstance: %s""".stripMargin.format(attributes.mkString(","), appVersionConfig, appConfigInstance))
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

      }

    }

    try {
      applicationConfigInstance(id) match {
        case None => throw new IllegalArgumentException(s"ApplicationConfigInstance not found for : $id")
        case Some(appConfigInstance) =>
          applicationVersionConfig(id.versionId) match {
            case None => throw new IllegalStateException("No ApplicationVersionConfig was found for :" + id.versionId)
            case Some(appVersionConfig) =>
              validateConfigSchema(appVersionConfig, appConfigInstance)
              runValidators(appVersionConfig, appConfigInstance)
              validateComponentDependencies(appVersionConfig, appConfigInstance)
              None
          }
      }
    } catch {
      case e: Exception => Some(e)
    }
  }
}

object ComponentConfigs {

  def getComponentDependencyRefs(versionConfig: Config, instanceConfig: Config): Option[Map[String, ComponentConfigInstanceId]] = {
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
        Some(refs.foldLeft(Map.empty[String, ComponentConfigInstanceId]) { (map, config) =>
          val compId = ComponentId(group = config.getString("group"), name = config.getString("name"))
          val versionId = getComponentDependencyVersionId(versionConfig, compId)
          val compConfigInstanceId = ComponentConfigInstanceId(versionId = versionId, configInstanceName = config.getString("config-ref-name"))
          val configName = getString(config, "config-name").getOrElse(compId.name)
          map + (configName -> compConfigInstanceId)
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

  def componentConfigInstanceIds(id: ComponentVersionId): Option[Iterable[ComponentConfigInstanceId]] = {
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
                      val compConfigInstanceIds = configInstances.foldLeft(List.empty[ComponentConfigInstanceId]) { (compConfigInstanceIds, configInstance) =>
                        ComponentConfigInstanceId(id, configInstance.getString("name")) :: compConfigInstanceIds
                      }
                      Some(compConfigInstanceIds)
                  }
              }
          }
      }
    }
  }

  /**
   * if the configuration is invalid, then an Exception is returned
   */
  def componentConfigInstance(id: ComponentConfigInstanceId): Option[ComponentConfigInstance] = {
    def compDependencyRefs(configInstance: Config, compVersionConfig: Option[com.azaptree.config.ComponentVersionConfig]): Option[List[com.azaptree.config.ComponentConfigInstanceId]] = {
      getConfigList(configInstance, "component-dependency-refs") match {
        case None => None
        case Some(compDependencyRefs) =>
          val refs = compDependencyRefs.foldLeft(List.empty[ComponentConfigInstanceId]) { (refs, compDependencyRef) =>
            val compId = ComponentId(group = compDependencyRef.getString("group"), name = compDependencyRef.getString("name"))
            val version = compVersionConfig.get.compVersion.compDependency(compId) match {
              case None => throw new IllegalStateException(s"Invalid component dependency [$compId] is defined for : $compVersionConfig")
              case Some(compVersionId) => compVersionId.version
            }
            val compConfigInstanceId = ComponentConfigInstanceId(
              versionId = ComponentVersionId(compId = compId, version = version),
              configInstanceName = compDependencyRef.getString("config-ref-name"))

            compConfigInstanceId :: refs
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
                      configInstances.find(_.getString("name") == id.configInstanceName) match {
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
  def validate(compConfigInstanceId: ComponentConfigInstanceId): Option[Exception] = {
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