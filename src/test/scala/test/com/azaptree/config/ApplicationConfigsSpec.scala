package test.com.azaptree.config

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import com.azaptree.config._
import com.azaptree.application.model.ApplicationId
import com.azaptree.application.model.ApplicationVersionId
import com.azaptree.application.model.ApplicationInstanceId

case class ApplicationConfigs(override val config: Config) extends com.azaptree.config.ApplicationConfigs

class ApplicationConfigsSpec extends FunSpec with ShouldMatchers {

  val log = LoggerFactory.getLogger("ApplicationConfigsSpec")

  val compConfig = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/reference.json")
  val appConfig = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/application.json")
  val config = appConfig.withFallback(compConfig)
  log.info(toFormattedJson(config))

  val appConfigs = ApplicationConfigs(config)

  for {
    appIds <- appConfigs.applicationIds
  } yield {
    appIds.foreach { appId =>
      log.info("========================= APPLICATION ============================================")
      log.info(s"appId : $appId")
      for {
        appVersionIds <- appConfigs.applicationVersionIds(appId)
      } yield {
        log.info("========================= APPLICATION VERSION ============================================")
        appVersionIds.foreach { appVersionId =>
          log.info(s"appVersionId : $appVersionId")
          for {
            appVersionConfig <- appConfigs.applicationVersionConfig(appVersionId)
          } yield {
            log.info("====================== APPLICATION VERSION CONFIG ===============================================")
            log.info("appVersionConfig : {}", appVersionConfig);
          }
        }
      }
    }
  }

  describe("ApplicationConfigs") {
    it("can list all found ApplicationIds") {
      appConfigs.applicationIds match {
        case None => throw new IllegalStateException("Expecting some ApplicationIds")
        case Some(appIds) => appIds.foreach(id => log.info(id.toString))
      }
    }

    it("can list all ApplicationVersionIds for an ApplicationId") {
      appConfigs.applicationIds match {
        case None => throw new IllegalStateException("Expecting some ApplicationIds")
        case Some(appIds) =>
          appIds.foreach { appId =>
            appConfigs.applicationVersionIds(appId) match {
              case None => throw new IllegalStateException("Found application which has no versions: " + appId)
              case Some(appVersionIds) => appVersionIds.foreach(id => log.info(id.toString))
            }
          }
      }
    }

    it("can find ApplicationVersions for a specified ApplicationVersionId") {
      appConfigs.applicationIds match {
        case None => throw new IllegalStateException("Expecting some ApplicationIds")
        case Some(appIds) =>
          appIds.foreach { appId =>
            appConfigs.applicationVersionIds(appId) match {
              case None => throw new IllegalStateException("Found application which has no versions: " + appId)
              case Some(appVersionIds) =>
                appVersionIds.foreach { id =>
                  appConfigs.applicationVersion(id) match {
                    case None => throw new IllegalStateException(s"Failed to find ApplicationVersion for $id")
                    case Some(appVersion) => log.info(appVersion.toString())
                  }
                }
            }
          }
      }
    }

    it("can list ApplicationConfigInstanceIds for a specified ApplicationVersionId") {
      appConfigs.applicationIds match {
        case None => throw new IllegalStateException("Expecting some ApplicationIds")
        case Some(appIds) =>
          appIds.foreach { appId =>
            appConfigs.applicationVersionIds(appId) match {
              case None => throw new IllegalStateException("Found application which has no versions: " + appId)
              case Some(appVersionIds) =>
                appVersionIds.foreach { id =>
                  appConfigs.applicationConfigInstanceIds(id) match {
                    case None => throw new IllegalStateException(s"Failed to find ApplicationConfigInstanceIds for $id")
                    case Some(applicationConfigInstanceIds) =>
                      applicationConfigInstanceIds.foreach(id => log.info(id.toString()))
                  }
                }
            }
          }
      }
    }

    it("can retrieve an ApplicationConfigInstance for a specified ApplicationConfigInstanceId") {
      appConfigs.applicationIds match {
        case None => throw new IllegalStateException("Expecting some ApplicationIds")
        case Some(appIds) =>
          appIds.foreach { appId =>
            appConfigs.applicationVersionIds(appId) match {
              case None => throw new IllegalStateException("Found application which has no versions: " + appId)
              case Some(appVersionIds) =>
                appVersionIds.foreach { id =>
                  appConfigs.applicationConfigInstanceIds(id) match {
                    case None => throw new IllegalStateException(s"Failed to find ApplicationConfigInstanceIds for $id")
                    case Some(applicationConfigInstanceIds) =>
                      applicationConfigInstanceIds.foreach { id =>
                        appConfigs.applicationConfigInstance(id) match {
                          case None => throw new IllegalStateException(s"Failed to find ApplicationConfigInstance for: $id")
                          case Some(configInstance) => log.info(configInstance.toString())
                        }
                      }
                  }
                }
            }
          }
      }
    }

    it("can validate an ApplicationConfigInstance") {
      appConfigs.applicationIds match {
        case None => throw new IllegalStateException("Expecting some ApplicationIds")
        case Some(appIds) =>
          appIds.foreach { appId =>
            appConfigs.applicationVersionIds(appId) match {
              case None => throw new IllegalStateException("Found application which has no versions: " + appId)
              case Some(appVersionIds) =>
                appVersionIds.foreach { id =>
                  appConfigs.applicationConfigInstanceIds(id) match {
                    case None => throw new IllegalStateException(s"Failed to find ApplicationConfigInstanceIds for $id")
                    case Some(applicationConfigInstanceIds) =>
                      applicationConfigInstanceIds.foreach { id =>
                        appConfigs.validate(id) match {
                          case None => // expected to be valid 
                          case Some(e) => throw e
                        }
                      }
                  }
                }
            }
          }
      }
    }

    it("can detect when an ApplicationConfigInstance is invalid") {
      val compConfig = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/reference.json")
      val appConfig = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/application-invalid-config.json")
      val config = appConfig.withFallback(compConfig)
      val appConfigs = ApplicationConfigs(config)

      val appId = ApplicationId(group = "com.azaptree", name = "application-security-server")

      val appConfigInstanceIds = {
        ApplicationInstanceId(ApplicationVersionId(appId = appId, version = "1.0.0"), instance = "missing-config-schema-but-instance-has-config") ::
          ApplicationInstanceId(ApplicationVersionId(appId = appId, version = "1.1.0"), instance = "invalid-config") ::
          ApplicationInstanceId(ApplicationVersionId(appId = appId, version = "1.1.0"), instance = "invalid-comp-dependency-ref") ::
          ApplicationInstanceId(ApplicationVersionId(appId = appId, version = "1.1.0"), instance = "invalid-comp-dependency-ref-config-ref") ::
          ApplicationInstanceId(ApplicationVersionId(appId = appId, version = "1.1.0"), instance = "non-matching-attribute-value") ::
          Nil
      }

      appConfigInstanceIds.foreach { id =>
        info("checking that config instance is invalid: " + id.instance)
        appConfigs.validate(id) match {
          case None => throw new Exception("Expected application config instance to be invalid: " + appConfigs.applicationConfigInstance(id) +
            "\n\n" + appConfigs.applicationVersionConfig(id.versionId))
          case Some(e) => log.info(s"$id is invalid - as expected : $e")
        }

      }

    }

    it("can convert an ApplicationVersionConfig to a Config") {
      appConfigs.applicationIds match {
        case None => throw new IllegalStateException("Expecting some ApplicationIds")
        case Some(appIds) =>
          appIds.foreach { appId =>
            appConfigs.applicationVersionIds(appId) match {
              case None => throw new IllegalStateException("Found application which has no versions: " + appId)
              case Some(appVersionIds) =>
                appVersionIds.foreach { id =>
                  import com.azaptree.config.ConfigConversions._
                  val appVersionConfig = appConfigs.applicationVersionConfig(id).get
                  val config: Config = appVersionConfig
                  log.info(s"""appInstance
                        |
                        | ${toFormattedJson(config)}
                        """.stripMargin)
                }
            }
          }
      }
    }

    it("can convert an ApplicationConfigInstance to a Config") {
      appConfigs.applicationIds match {
        case None => throw new IllegalStateException("Expecting some ApplicationIds")
        case Some(appIds) =>
          appIds.foreach { appId =>
            appConfigs.applicationVersionIds(appId) match {
              case None => throw new IllegalStateException("Found application which has no versions: " + appId)
              case Some(appVersionIds) =>
                appVersionIds.foreach { id =>

                  import com.azaptree.config.ConfigConversions._
                  val appVersionConfig = appConfigs.applicationVersionConfig(id).get
                  val config: Config = appVersionConfig
                  log.info(s"""appInstance
                        |
                        | ${toFormattedJson(config)}
                        """.stripMargin)

                  appConfigs.applicationConfigInstanceIds(id) match {
                    case None => throw new IllegalStateException(s"Failed to find ApplicationConfigInstanceIds for $id")
                    case Some(applicationConfigInstanceIds) =>
                      applicationConfigInstanceIds.foreach { id =>
                        val appInstance = appConfigs.applicationConfigInstance(id).get
                        import com.azaptree.config.ConfigConversions._
                        val config: Config = appInstance
                        log.info(s"""appInstance
                        |
                        | ${toFormattedJson(config)}
                        """.stripMargin)
                      }
                  }
                }
            }
          }
      }
    }

  }

}