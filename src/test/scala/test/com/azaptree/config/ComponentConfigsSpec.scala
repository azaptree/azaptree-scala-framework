package test.com.azaptree.config

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import com.azaptree.application.model.ComponentId
import java.util.UUID
import com.typesafe.config.ConfigRenderOptions

case class ComponentConfigs(override val config: Config) extends com.azaptree.config.ComponentConfigs

class ComponentConfigsSpec extends FunSpec with ShouldMatchers {
  val log = LoggerFactory.getLogger("ComponentConfigsSpec")

  val config = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/reference.json")

  val renderOptions = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)
  log.info(config.root().render(renderOptions))

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
  }

}