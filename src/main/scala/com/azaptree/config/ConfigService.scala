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
            Right(Some(compDependencyRefs.foldLeft(config) { (c, compDependencyRefId) =>
              componentConfig(compDependencyRefId) match {
                case Left(e) => throw e
                case Right(None) => c
                case Right(Some(compConfig)) => c.withFallback(compConfig)
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
            Right(Some(compDependencyRefs.foldLeft(config) { (config, compDependencyRefId) =>
              componentConfig(compDependencyRefId) match {
                case Right(None) => config
                case Right(Some(compConfig)) => config.withFallback(compConfig)
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
                    val compDependencies = compDependencyConfigs.foldLeft(List.empty[ComponentVersionId]) { (list, config) =>
                      val compId = ComponentId(group = config.getString("group"), name = config.getString("name"))
                      ComponentVersionId(compId = compId, version = config.getString("version")) :: list
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
    def getComponentDependencyRefs(versionConfig: Config, instanceConfig: Config): Option[Iterable[ComponentConfigInstanceId]] = {
      getConfigList(instanceConfig, "component-dependency-refs") match {
        case None => None
        case Some(refs) =>
          Some(refs.foldLeft(List.empty[ComponentConfigInstanceId]) { (list, config) =>
            val compId = ComponentId(group = config.getString("group"), name = config.getString("name"))
            val versionId = getComponentDependencyVersionId(versionConfig, compId)
            ComponentConfigInstanceId(versionId = versionId, configInstanceName = config.getString("configName")) :: list
          })
      }
    }

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
                          compDependencyRefs = getComponentDependencyRefs(versionConfig, instanceConfig)))
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

    def getComponentDependencies(versionConfig: Config): Option[Iterable[ComponentVersionId]] = {
      getConfigList(versionConfig, "component-dependencies") match {
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
                  ApplicationVersion(id = versionId, dependencies = getComponentDependencies(versionConfig)),
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
          if (appConfigInstance.compDependencyRefs.isEmpty || appConfigInstance.compDependencyRefs.get.size != compDependencies.size) {
            throw new IllegalStateException("The number of component dependency refs defined does not match the number of component dependencies defined in the application version config")
          }

          val refs = appConfigInstance.compDependencyRefs.get

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
                  getConfigList(versionConfig, "component-dependencies") match {
                    case None => Some(ComponentVersion(id))
                    case Some(compDependencies) =>
                      val compVersionIds = compDependencies.foldLeft(List.empty[ComponentVersionId]) { (compVersionIds, compDependencyConfig) =>
                        val compId = ComponentId(group = compDependencyConfig.getString("group"), name = compDependencyConfig.getString("name"))
                        ComponentVersionId(compId, compDependencyConfig.getString("version")) :: compVersionIds
                      }

                      Some(ComponentVersion(id, Some(compVersionIds)))
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
              configInstanceName = compDependencyRef.getString("configName"))

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
                        case Some(configInstance) =>
                          val compVersionConfig = componentVersionConfig(id.versionId)
                          val configInstanceConfig = getConfig(configInstance, "config")
                          Some(ComponentConfigInstance(id = id, config = configInstanceConfig, compDependencyRefs = compDependencyRefs(configInstance, compVersionConfig)))
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
                case Some(version) =>
                  Some(ComponentVersionConfig(
                    ComponentVersion(id = versionId, compDependencies = compDependencies(version)),
                    configSchema = getConfig(version, "config-schema"),
                    validators = validators(version)))
              }
          }
      }
    }
  }

  /**
   * Will return an IllegalArgumentException if the compConfigInstanceId was not found
   */
  def validate(compConfigInstanceId: ComponentConfigInstanceId): Option[Exception] = {
    def checkThatAllDependenciesAreFullfilled(versionConfig: ComponentVersionConfig, compDependencyRefs: Option[Iterable[ComponentConfigInstanceId]]) = {
      versionConfig.compVersion.compDependencies match {
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
              checkThatAllDependenciesAreFullfilled(versionConfig, instance.compDependencyRefs)
              None
          }
      }
    } catch {
      case e: Exception => Some(e)
    }

  }
}