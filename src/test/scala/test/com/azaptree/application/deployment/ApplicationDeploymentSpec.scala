package test.com.azaptree.application.deployment

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.application.deployment._
import com.azaptree.application.deployment.applicationDeploymentConfigParams._
import com.azaptree.application.ApplicationService
import com.azaptree.application.pidFile.ApplicationPidFile
import com.azaptree.application.Component
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.nio.file.FileWatcherService
import com.azaptree.nio.file.FileWatcherServiceComponentLifeCycle
import com.azaptree.application.ApplicationExtension
import com.azaptree.application.ApplicationExtensionComponentLifeCycle
import test.com.azaptree.application.pidFile.ApplicationPidFileUsingApplictionDeploymentSpec
import java.io.File
import java.io.FilenameFilter

class ApplicationDeploymentSpec extends FunSpec with ShouldMatchers {

  implicit val appService = new ApplicationService()

  val fileWatcherComponent = Component[ComponentNotConstructed, FileWatcherService]("FileWatcherService", new FileWatcherServiceComponentLifeCycle())
  implicit val fileWatcherService = appService.registerComponent(fileWatcherComponent).get

  System.setProperty("config.resource", "ApplicationDeploymentSpec.conf")

  val appConfigRoot = ApplicationConfigRoot("test/com/azaptree/application/deployment/config/applications/applications.conf")
  val compConfigRoot = ComponentConfigRoot("test/com/azaptree/application/deployment/config/components/components.conf")
  val namespace = Namespace("com.azaptree")

  val applicationDeploymentConfig: ApplicationDeploymentConfig = () => {
    loadLocalApplicationDeploymentConfig(appConfigRoot, compConfigRoot, namespace) match {
      case Left(e) => throw e
      case Right(config) => config match {
        case None => throw new IllegalStateException("The application config was not found")
        case Some(c) => c
      }
    }
  }

  val appDeployment = ApplicationDeployment(applicationDeploymentConfig)

  appService.registerComponent(Component[ComponentNotConstructed, ApplicationExtension]("ApplicationDeployment", new ApplicationExtensionComponentLifeCycle(appDeployment)))
  val appPidFile = appDeployment.appPidFile

  def listPidFiles(pidDir: File) = {
    pidDir.listFiles(new FilenameFilter() {
      override def accept(dir: File, name: String): Boolean = {
        name.endsWith(".pid")
      }
    })
  }

  describe("An ApplicationDeployment") {
    it("can be created using an ApplicationDeployment") {
      val pidDir = appPidFile.pidFile.getParentFile()
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