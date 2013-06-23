package test.com.azaptree.config

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import com.azaptree.config._

case class ConfigService(override val config: Config) extends com.azaptree.config.ConfigService

class ConfigServiceTest extends FunSpec with ShouldMatchers {

  val log = LoggerFactory.getLogger("ConfigServiceTest")

  val compConfig = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/reference.json")
  val appConfig = ConfigFactory.parseResourcesAnySyntax("test/com/azaptree/config/application.json")
  val config = appConfig.withFallback(compConfig)
  log.info(toFormattedJson(config))

  val configService = ConfigService(config)

  describe("ConfigService") {

    it("can return the configuration for a ComponentConfigInstance") {
      for {
        componentIds <- configService.componentIds
      } yield {
        componentIds.foreach { componentId =>
          for {
            componentVersionIds <- configService.componentVersionIds(componentId)
          } yield {
            componentVersionIds.foreach { compVersionId =>
              for {
                compConfigInstanceIds <- configService.componentConfigInstanceIds(compVersionId)
              } yield {
                compConfigInstanceIds.foreach { compConfigInstanceId =>
                  configService.componentConfig(compConfigInstanceId) match {
                    case Left(e) => throw e
                    case Right(None) => throw new IllegalStateException(s"No Config was found for : $compConfigInstanceId")
                    case Right(Some(config)) =>
                      log.info(s"$compConfigInstanceId config :\n" + toFormattedJson(config))
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