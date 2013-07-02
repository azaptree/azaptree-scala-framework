package com.azaptree.config

import com.azaptree.application.model.ApplicationId
import com.azaptree.application.model.ApplicationVersion
import com.azaptree.application.model.ApplicationVersionId
import org.slf4j.LoggerFactory
import com.typesafe.config.Config
import com.azaptree.application.model.ComponentDependency
import com.azaptree.application.model.ComponentId
import com.azaptree.application.model.ComponentVersionId
import com.azaptree.application.model.ComponentDependencyConfig
import com.azaptree.application.model.ApplicationInstanceId

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

  def applicationConfigInstanceIds(id: ApplicationVersionId): Option[Iterable[ApplicationInstanceId]] = {
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
                    val appInstanceIds = instanceConfigs.foldLeft(List.empty[ApplicationInstanceId]) { (list, config) =>
                      ApplicationInstanceId(id, config.getString("name")) :: list
                    }
                    Some(appInstanceIds)
                }
            }
        }
    }
  }

  def applicationConfigInstance(id: ApplicationInstanceId): Option[ApplicationInstanceConfig] = {
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
                    instanceConfigs.find(_.getString("name") == id.instance) match {
                      case None => None
                      case Some(instanceConfig) =>
                        Some(ApplicationInstanceConfig(
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

  def validate(id: ApplicationInstanceId): Option[Exception] = {

    def validateConfigSchema(appVersionConfig: ApplicationVersionConfig, appConfigInstance: ApplicationInstanceConfig) = {
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

    def runValidators(appVersionConfig: ApplicationVersionConfig, appConfigInstance: ApplicationInstanceConfig) = {
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

    def validateComponentDependencies(appVersionConfig: ApplicationVersionConfig, appConfigInstance: ApplicationInstanceConfig) = {
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
              throw new IllegalStateException(s"""ComponentConfigInstance not found for : ${ref._2}
              
              while validating: ${id}
              """);
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
                    case None => throw new IllegalStateException("ComponentDependency not satisfied for : %s\n\n%s".format(compDependencyConfig.name, compDependency));
                    case Some(compRefConfigInstanceId) =>
                      compDependencyConfig.attributes.foreach { attributes =>
                        componentConfigInstance(compRefConfigInstanceId) match {
                          case None => throw new IllegalStateException("""ComponentConfigInstance was not found for: %s"
                            | appVersionConfig : %s
                            | appConfigInstance : %s""".stripMargin.format(compRefConfigInstanceId, appVersionConfig, appConfigInstance))
                          case Some(compRefConfigInstance) => validate(compRefConfigInstanceId) match {
                            case Some(exception) => throw exception
                            case None =>
                              applicationConfigsLog.debug("checking attributes match")
                              if (compRefConfigInstance.attributes.isEmpty) {
                                throw new IllegalStateException(s"The referenced component dependency instance is missing required attributes : $attributes\n$compRefConfigInstance");
                              }

                              val refAttributes = compRefConfigInstance.attributes.get

                              attributes.foreach { attribute =>
                                refAttributes.get(attribute._1) match {
                                  case None => throw new IllegalStateException(s"The referenced component dependency instance is missing required attribute : $attribute\n$compRefConfigInstance");
                                  case Some(refAttrValue) => if (refAttrValue != attribute._2) throw new IllegalStateException(s"The referenced component dependency instance is missing required attribute : $attribute\n$compRefConfigInstance");
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

