package test.com.azaptree.application.pidFile

import java.io.File
import java.io.FilenameFilter
import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.application.ApplicationExtension
import com.azaptree.application.ApplicationExtensionComponentLifeCycle
import com.azaptree.application.ApplicationLauncher
import com.azaptree.application.ApplicationService
import com.azaptree.application.Component
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.application.pidFile.ApplicationPidFile
import com.azaptree.nio.file.FileWatcherService
import com.azaptree.nio.file.FileWatcherServiceComponentLifeCycle
import com.typesafe.config._
import com.azaptree.application.deployment.ApplicationDeployment

import org.slf4j.LoggerFactory

object ApplicationPidFileSpec extends ApplicationLauncher {
  val pidDir = new File("target/tests/ApplicationPidFileSpec")

  override def createApplicationService(): ApplicationService = {

    implicit val appService = new ApplicationService()

    val fileWatcherComponent = Component[ComponentNotConstructed, FileWatcherService]("FileWatcherService", new FileWatcherServiceComponentLifeCycle())
    implicit val fileWatcherService = appService.registerComponent(fileWatcherComponent).get

    val appPidFile = ApplicationPidFile("ApplicationPidFile", pidDir)
    appService.registerComponent(Component[ComponentNotConstructed, ApplicationExtension]("ApplicationPidFile", new ApplicationExtensionComponentLifeCycle(appPidFile)))

    appService
  }
}

object ApplicationPidFileUsingApplictionDeploymentSpec extends ApplicationLauncher {
  val log = LoggerFactory.getLogger("ApplicationPidFileUsingApplictionDeploymentSpec")

  var appPidFile: ApplicationPidFile = _

  override def createApplicationService(): ApplicationService = {

    val config = ConfigFactory.parseString("""
com.azaptree{
   app-instance-id{
      group = "com.azaptree"
      name = "config-service"
      version = "1.0.0"
      instance = "dev-local"
   }
 
   config-service{
      url = "http://localhost:8080/api/config-service/1-0-0/"${com.azaptree.app-instance-id.group}/${com.azaptree.app-instance-id.name}/${com.azaptree.app-instance-id.version}/${com.azaptree.app-instance-id.instance}
   }
}
""").resolve()

    log.info("com.azaptree.config-service.url = {}", config.getString("com.azaptree.config-service.url"))
    log.info("com.azaptree.app-instance-id.group = {}", config.getString("com.azaptree.app-instance-id.group"))
    log.info("com.azaptree.app-instance-id.group = {}", config.getString("com.azaptree.app-instance-id.name"))
    log.info("com.azaptree.app-instance-id.group = {}", config.getString("com.azaptree.app-instance-id.version"))
    log.info("com.azaptree.app-instance-id.group = {}", config.getString("com.azaptree.app-instance-id.instance"))

    implicit val appService = new ApplicationService()

    val fileWatcherComponent = Component[ComponentNotConstructed, FileWatcherService]("FileWatcherService", new FileWatcherServiceComponentLifeCycle())
    implicit val fileWatcherService = appService.registerComponent(fileWatcherComponent).get

    import com.azaptree.application.deployment._

    val deploymentConfig: ApplicationDeploymentConfig = () => config

    val appDeployment = ApplicationDeployment(deploymentConfig)
    appService.registerComponent(Component[ComponentNotConstructed, ApplicationExtension]("ApplicationDeployment", new ApplicationExtensionComponentLifeCycle(appDeployment)))
    ApplicationPidFileUsingApplictionDeploymentSpec.appPidFile = appDeployment.appPidFile

    appService
  }
}

import ApplicationPidFileSpec._

class ApplicationPidFileSpec extends FunSpec with ShouldMatchers {
  def listPidFiles(pidDir: File) = {
    pidDir.listFiles(new FilenameFilter() {
      override def accept(dir: File, name: String): Boolean = {
        name.endsWith(".pid")
      }
    })
  }

  describe("An ApplicationPidFile") {
    it("can create a PID file on start up") {
      val appService = createApplicationService()
      try {
        val pidFilesAfterStarting = listPidFiles(ApplicationPidFileSpec.pidDir)
        pidFilesAfterStarting.length should be(1)
      } finally {
        appService.stop()
      }
    }

    it("it will stop the application when the PID file is deleted") {
      val appService = createApplicationService()
      try {
        val pidFilesAfterStarting = listPidFiles(ApplicationPidFileSpec.pidDir)

        pidFilesAfterStarting.length should be(1)
        pidFilesAfterStarting(0).delete()

        val pidFilesAfterStopping = listPidFiles(ApplicationPidFileSpec.pidDir)
        pidFilesAfterStopping.length should be(0)

        Thread.sleep(100l)
        appService.isRunning() should be(false)
      } finally {
        appService.stop()
      }
    }

    it("can be created using an ApplicationDeployment") {
      val appService = ApplicationPidFileUsingApplictionDeploymentSpec.createApplicationService()
      val pidDir = ApplicationPidFileUsingApplictionDeploymentSpec.appPidFile.pidFile.getParentFile()
      try {
        val pidFilesAfterStarting = listPidFiles(pidDir)

        pidFilesAfterStarting.length should be(1)
        info("pidFile = " + pidFilesAfterStarting(0))
        pidFilesAfterStarting(0).delete()

        val pidFilesAfterStopping = listPidFiles(pidDir)
        pidFilesAfterStopping.length should be(0)

        Thread.sleep(100l)
        appService.isRunning() should be(false)
      } finally {
        appService.stop()
      }
    }
  }
}