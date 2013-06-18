package test.com.azaptree.nio.file

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.application.ApplicationService
import com.azaptree.nio.file.WatchEventProcessor
import org.slf4j.LoggerFactory
import java.nio.file.Path
import FileWatcherServiceComponentSpec._
import com.azaptree.application.Component
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.nio.file.FileWatcherService
import com.azaptree.nio.file.FileWatcherServiceComponentLifeCycle
import java.nio.file.Files
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.UUID
import org.scalatest.BeforeAndAfterAll

object FileWatcherServiceComponentSpec {
  val log = LoggerFactory.getLogger("FileWatcherServiceComponentSpec")

  val baseDir = new File("target/tests/FileWatcherServiceComponentSpec")

  var pathsChanged: List[Path] = Nil

  val fileChangedListener: WatchEventProcessor = watchEvent => {
    log.info("watchEvent: context=%s, count=%d, name=%s, type=%s".format(
      watchEvent.context(), watchEvent.count, watchEvent.kind().name(), watchEvent.kind().`type`))
    watchEvent.context() match {
      case p: Path =>
        pathsChanged = p :: pathsChanged
    }

  }
}

class FileWatcherServiceComponentSpec extends FunSpec with ShouldMatchers with BeforeAndAfterAll {

  override def beforeAll() = {
    FileUtils.deleteDirectory(baseDir)
    baseDir.mkdirs()
  }

  describe("A FileWatcherServiceComponent") {

    it("can be plugged into an ApplicationService") {
      val appService = new ApplicationService()

      try {
        val fileWatcherServiceComponent = Component[ComponentNotConstructed, FileWatcherService](
          name = "FileWatcherService",
          componentLifeCycle = new FileWatcherServiceComponentLifeCycle())

        appService.registerComponent(fileWatcherServiceComponent)

        val fileWatcherService = appService.getStartedComponentObject[FileWatcherService](fileWatcherServiceComponent.name).get

        val path = new File(baseDir, UUID.randomUUID().toString()).toPath()
        Files.createDirectory(path)
        val result = fileWatcherService.watch(path = path, fileWatcher = fileChangedListener)
        result match {
          case Right(key) => log.info(key.toString())
          case Left(e) => throw e
        }

        for (i <- 1 to 10) {
          val f = new File(path.toFile(), UUID.randomUUID().toString())
          FileUtils.touch(f)
        }

        Thread.sleep(1000l)

        log.info("fileWatcherServiceServiceSpec.pathsChanged.size = {}", pathsChanged.size)

        pathsChanged.size should be >= (20)

        val sizeBefore = pathsChanged.size
        fileWatcherService.cancel(result.right.get).isDefined should be(true)

        for (i <- 1 to 10) {
          val f = new File(path.toFile(), UUID.randomUUID().toString())
          FileUtils.touch(f)
        }

        Thread.sleep(100l)
        pathsChanged.size should be(sizeBefore)
      } finally {
        appService.stop()
      }

    }

  }

}