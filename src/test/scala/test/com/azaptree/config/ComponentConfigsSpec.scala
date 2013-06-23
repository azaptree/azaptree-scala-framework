package test.com.azaptree.config

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import com.azaptree.application.model.ComponentId
import java.util.UUID
import com.typesafe.config.ConfigRenderOptions
import com.azaptree.config._
import com.azaptree.application.model.ComponentVersionId

case class ComponentConfigs(override val config: Config) extends com.azaptree.config.ComponentConfigs

class AlwaysValid extends ConfigValidator {
  override def validate(config: Config) = {
    None
  }
}

class ComponentConfigsSpec extends FunSpec with ShouldMatchers {
  val log = LoggerFactory.getLogger("ComponentConfigsSpec")

  val config = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/reference.json")

  log.info(toFormattedJson(config))

  val compConfigs = ComponentConfigs(config)

  describe("ComponentConfigs") {
    it("can list the ComponentIds that are available") {
      val compIds = compConfigs.componentIds
      compIds.isDefined should be(true)
      for {
        ids <- compIds
      } yield {
        ids.foreach(id => log.info(id.toString()))

        assert(ids.find(compId => compId.group == "com.azaptree" && compId.name == "azaptree-security-service").isDefined)
        assert(ids.find(compId => compId.group == "com.azaptree" && compId.name == "azaptree-security-model").isDefined)
        assert(ids.find(compId => compId.group == "com.azaptree" && compId.name == "azaptree-cassandra").isDefined)
        assert(ids.find(compId => compId.group == "com.azaptree" && compId.name == UUID.randomUUID().toString()).isEmpty)
        assert(ids.find(compId => compId.group == UUID.randomUUID().toString() && compId.name == "azaptree-cassandra").isEmpty)
      }
    }

    it("can list ComponentVersionIds for a ComponentId") {
      val compIds = compConfigs.componentIds
      compIds.isDefined should be(true)
      for {
        ids <- compIds
      } yield {
        ids.foreach { id =>
          compConfigs.componentVersions(ComponentId(group = id.group, name = id.name)) match {
            case None => throw new IllegalStateException("Did not find component versions for: " + id)
            case Some(versionIds) =>
              versionIds.foreach(id => log.info(id.toString()))
          }
        }

      }
    }

    it("can retrieve a ComponentVersion for a specified ComponentVersionId") {
      val compIds = compConfigs.componentIds
      compIds.isDefined should be(true)
      for {
        ids <- compIds
      } yield {
        ids.foreach { id =>
          compConfigs.componentVersions(ComponentId(group = id.group, name = id.name)) match {
            case None => throw new IllegalStateException("Did not find component versions for: " + id)
            case Some(versionIds) =>
              versionIds.foreach { versionId =>
                compConfigs.componentVersion(versionId) match {
                  case None => throw new IllegalStateException("Failed to find ComponentVersion for: " + versionId)
                  case Some(compVersion) => log.info(compVersion.toString())
                }
              }
          }
        }

      }
    }

    it("can list ComponentConfigInstanceId for a specified ComponentVersionId") {
      val compIds = compConfigs.componentIds
      compIds.isDefined should be(true)
      for {
        ids <- compIds
      } yield {
        ids.foreach { id =>
          compConfigs.componentVersions(ComponentId(group = id.group, name = id.name)) match {
            case None => throw new IllegalStateException("Did not find component versions for: " + id)
            case Some(versionIds) =>
              versionIds.foreach { versionId =>
                compConfigs.componentConfigInstanceIds(versionId) match {
                  case None => throw new IllegalStateException("Failed to find ComponentInstanceIds for: " + versionId)
                  case Some(compInstanceIds) =>
                    compInstanceIds.foreach(id => log.info(id.toString()))
                }
              }
          }
        }
      }
    }

    it("can return the ComponentVersionConfig for the specified ComponentVersionId") {
      val compIds = compConfigs.componentIds
      compIds.isDefined should be(true)
      for {
        ids <- compIds
      } yield {
        ids.foreach { id =>
          compConfigs.componentVersions(ComponentId(group = id.group, name = id.name)) match {
            case None => throw new IllegalStateException("Did not find component versions for: " + id)
            case Some(versionIds) =>
              versionIds.foreach { versionId =>
                compConfigs.componentVersionConfig(versionId) match {
                  case None => throw new IllegalStateException("Failed to find ComponentVersionConfig for: " + versionId)
                  case Some(componentVersionConfig) =>
                    log.info(componentVersionConfig.toString())
                }
              }
          }
        }
      }
    }

    it("can return the ComponentConfigInstance for the specified ComponentConfigInstanceId") {
      val compIds = compConfigs.componentIds
      compIds.isDefined should be(true)
      for {
        ids <- compIds
      } yield {
        ids.foreach { id =>
          compConfigs.componentVersions(ComponentId(group = id.group, name = id.name)) match {
            case None => throw new IllegalStateException("Did not find component versions for: " + id)
            case Some(versionIds) =>
              versionIds.foreach { versionId =>
                compConfigs.componentConfigInstanceIds(versionId) match {
                  case None => throw new IllegalStateException("Expected some ComponentConfigInstanceIds")
                  case Some(componentConfigInstanceIds) =>
                    componentConfigInstanceIds.foreach { componentConfigInstanceId =>
                      compConfigs.componentConfigInstance(componentConfigInstanceId) match {
                        case None => throw new IllegalStateException("Expected componentConfigInstance to be found for: " + componentConfigInstanceId)
                        case Some(componentConfigInstance) =>
                          log.info(componentConfigInstance.toString())
                      }
                    }
                }
              }
          }
        }
      }
    }

    it("can validate ComponentConfigInstances") {
      val compIds = compConfigs.componentIds
      compIds.isDefined should be(true)
      for {
        ids <- compIds
      } yield {
        ids.foreach { id =>
          compConfigs.componentVersions(ComponentId(group = id.group, name = id.name)) match {
            case None => throw new IllegalStateException("Did not find component versions for: " + id)
            case Some(versionIds) =>
              versionIds.foreach { versionId =>
                compConfigs.componentConfigInstanceIds(versionId) match {
                  case None => throw new IllegalStateException("Expected some ComponentConfigInstanceIds")
                  case Some(componentConfigInstanceIds) =>
                    componentConfigInstanceIds.foreach { componentConfigInstanceId =>

                      compConfigs.componentConfigInstance(componentConfigInstanceId) match {
                        case None => throw new IllegalStateException("Expected componentConfigInstance to be found for: " + componentConfigInstanceId)
                        case Some(componentConfigInstance) =>
                          log.info(componentConfigInstance.toString())
                          compConfigs.validate(componentConfigInstanceId) match {
                            case None => // is valid
                            case Some(e) => throw e
                          }
                      }

                    }
                }
              }
          }
        }
      }

    }

    it("can detect when a ComponentConfigInstance is invalid") {
      val config = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/referenceWithInvalidConfigSchema.json")
      val compConfigs = ComponentConfigs(config)

      val invalidConfigs: List[ComponentConfigInstanceId] = {
        ComponentConfigInstanceId(ComponentVersionId(ComponentId("com.azaptree", "azaptree-security-service"), "1.1.0"), "dev-local") ::
          ComponentConfigInstanceId(ComponentVersionId(ComponentId("com.azaptree", "azaptree-security-service"), "1.2.0"), "invalid-dependency-ref") ::
          ComponentConfigInstanceId(ComponentVersionId(ComponentId("com.azaptree", "azaptree-security-service"), "1.2.0"), "missing-dependency-ref") ::
          Nil
      }

      invalidConfigs.foreach { componentConfigInstanceId =>
        compConfigs.validate(componentConfigInstanceId) match {
          case None => throw new Exception(s"$componentConfigInstanceId -> should be invalid")
          case Some(e) => log.info(s"Validation failed as epected for $componentConfigInstanceId")
        }
      }

    }

  }

}