package test.com.azaptree.config

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import com.azaptree.config._

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
        case Some(appIds) => appIds.foreach(appId => log.info(appId.toString))
      }
    }
  }

}