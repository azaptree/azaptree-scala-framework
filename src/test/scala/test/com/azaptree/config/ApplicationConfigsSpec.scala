package test.com.azaptree.config

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import com.azaptree.config._
import com.azaptree.application.model.ApplicationId
import com.azaptree.application.model.ApplicationVersionId

case class ApplicationConfigs(override val config: Config) extends com.azaptree.config.ApplicationConfigs

class ApplicationConfigsSpec extends FunSpec with ShouldMatchers {

  val log = LoggerFactory.getLogger("ApplicationConfigsSpec")

  val compConfig = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/reference.json")
  val appConfig = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/application.json")
  val config = appConfig.withFallback(compConfig)
  log.info(toFormattedJson(config))

  val appConfigs = ApplicationConfigs(config)

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
            appConfigs.applicationVersions(appId) match {
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
            appConfigs.applicationVersions(appId) match {
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
            appConfigs.applicationVersions(appId) match {
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
            appConfigs.applicationVersions(appId) match {
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
            appConfigs.applicationVersions(appId) match {
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
        ApplicationConfigInstanceId(ApplicationVersionId(appId = appId, version = "1.0.0"), configInstanceName = "missing-config-schema-but-instance-has-config") ::
          ApplicationConfigInstanceId(ApplicationVersionId(appId = appId, version = "1.1.0"), configInstanceName = "invalid-config") ::
          ApplicationConfigInstanceId(ApplicationVersionId(appId = appId, version = "1.1.0"), configInstanceName = "invalid-comp-dependency-ref") ::
          ApplicationConfigInstanceId(ApplicationVersionId(appId = appId, version = "1.1.0"), configInstanceName = "invalid-comp-dependency-ref-config-ref") ::
          Nil
      }

      appConfigInstanceIds.foreach { id =>
        appConfigs.validate(id) match {
          case None => throw new Exception(s"Expected application config instance to be invalid: $id")
          case Some(e) => log.info(s"$id is invalid - as expected : $e")
        }

      }

    }

  }

}