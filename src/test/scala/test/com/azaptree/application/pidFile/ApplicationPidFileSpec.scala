package test.com.azaptree.application.pidFile

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec
import com.azaptree.application.ApplicationLauncher
import com.azaptree.application.ApplicationService
import com.azaptree.application.Component
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.nio.file.FileWatcherService
import com.azaptree.nio.file.FileWatcherServiceComponentLifeCycle
import com.azaptree.application.pidFile.ApplicationPidFile
import java.io.File
import com.azaptree.application.ApplicationExtension
import com.azaptree.application.ApplicationExtensionComponentLifeCycle
import com.azaptree.application.AppLauncher
import java.io.FilenameFilter

object ApplicationPidFileSpec extends ApplicationLauncher {
  val pidDir = new File("target/tests/ApplicationPidFileSpec")

  override def createApplicationService(): ApplicationService = {

    implicit val appService = new ApplicationService()

    val fileWatcherComponent = Component[ComponentNotConstructed, FileWatcherService]("FileWatcherService", new FileWatcherServiceComponentLifeCycle())
    appService.registerComponent(fileWatcherComponent)
    implicit val fileWatcherService = appService.getStartedComponentObject[FileWatcherService](fileWatcherComponent.name).get

    val appPidFile = ApplicationPidFile("ApplicationPidFile", pidDir)
    appService.registerComponent(Component[ComponentNotConstructed, ApplicationExtension]("ApplicationPidFile", new ApplicationExtensionComponentLifeCycle(appPidFile)))

    appService
  }
}

import ApplicationPidFileSpec._

class ApplicationPidFileSpec extends FunSpec with ShouldMatchers {
  def listPidFiles() = {
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
        val pidFilesAfterStarting = listPidFiles()
        pidFilesAfterStarting.length should be(1)
      } finally {
        appService.stop()
      }
    }

    it("it will stop the application when the PID file is deleted") {
      val appService = createApplicationService()
      try {
        val pidFilesAfterStarting = listPidFiles()

        pidFilesAfterStarting.length should be(1)
        pidFilesAfterStarting(0).delete()

        val pidFilesAfterStopping = listPidFiles()
        pidFilesAfterStopping.length should be(0)

        Thread.sleep(100l)
        appService.isRunning() should be(false)
      } finally {
        appService.stop()
      }
    }
  }
}