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
  }

}