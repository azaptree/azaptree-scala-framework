package test.com.azaptree.nio.file

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.nio.file.FileWatcherService
import com.azaptree.nio.file._
import java.nio.file.Path
import org.slf4j.LoggerFactory
import java.io.File
import FileWatcherServiceSpec._
import java.util.UUID
import org.scalatest.BeforeAndAfterAll
import org.apache.commons.io.FileUtils
import java.nio.file.Files

object FileWatcher extends FileWatcherService {
}

object FileWatcherServiceSpec {
  val log = LoggerFactory.getLogger("FileWatcherServiceSpec")

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

class FileWatcherServiceSpec extends FunSpec with ShouldMatchers with BeforeAndAfterAll {

  val baseDir = new File("target/tests/FileWatcherServiceSpec")

  override def beforeAll() = {
    FileUtils.deleteDirectory(baseDir)
    baseDir.mkdirs()
  }

  describe("A FileWatcherService") {
    it("can be used to register file paths to watch") {
      val file1Path = baseDir.toPath()
      val result = FileWatcher.watch(path = file1Path, fileWatcher = fileChangedListener)
      result match {
        case Right(key) =>
          log.info(key.toString())
          val registration = FileWatcher.fileWatcherRegistration(key)
          registration match {
            case None => throw new IllegalStateException(s"no registration found for : $key")
            case Some(r) => log.info("registration = {}", r)
          }
        case Left(e) => throw e
      }
    }

    it("will return an Exception if the path does not exist") {
      val file = new File(UUID.randomUUID().toString())
      file.exists() should be(false)
      val filePath = file.toPath()
      val result = FileWatcher.watch(path = filePath, fileWatcher = fileChangedListener)
      result match {
        case Right(key) => throw new IllegalStateException(s"Should have received an exception because the path does not exist: $file")
        case Left(e) => log.info("expected exception because path does not exist", e)
      }
    }

    it("can cancel a FileWatcherRegistration") {
      val path = new File(baseDir, UUID.randomUUID().toString()).toPath()
      Files.createDirectory(path)
      val result = FileWatcher.watch(path = path, fileWatcher = fileChangedListener)
      result match {
        case Right(key) => log.info(key.toString())
        case Left(e) => throw e
      }

      for (i <- 1 to 10) {
        val f = new File(path.toFile(), UUID.randomUUID().toString())
        FileUtils.touch(f)
      }

      Thread.sleep(1000l)

      log.info("FileWatcherServiceSpec.pathsChanged.size = {}", FileWatcherServiceSpec.pathsChanged.size)

      FileWatcherServiceSpec.pathsChanged.size should be >= (20)

      val sizeBefore = FileWatcherServiceSpec.pathsChanged.size
      FileWatcher.cancel(result.right.get).isDefined should be(true)

      for (i <- 1 to 10) {
        val f = new File(path.toFile(), UUID.randomUUID().toString())
        FileUtils.touch(f)
      }

      Thread.sleep(100l)
      FileWatcherServiceSpec.pathsChanged.size should be(sizeBefore)
    }

    it("can notify registered listeners when a file path watch event has occurred") {
      val path = new File(baseDir, UUID.randomUUID().toString()).toPath()
      Files.createDirectory(path)
      val result = FileWatcher.watch(path = path, fileWatcher = fileChangedListener)
      result match {
        case Right(key) => log.info(key.toString())
        case Left(e) => throw e
      }

      for (i <- 1 to 10) {
        val f = new File(path.toFile(), UUID.randomUUID().toString())
        FileUtils.touch(f)
      }

      Thread.sleep(1000l)

      log.info("FileWatcherServiceSpec.pathsChanged.size = {}", FileWatcherServiceSpec.pathsChanged.size)

      FileWatcherServiceSpec.pathsChanged.size should be >= (20)
    }

    it("can be used to query which file paths are being watched") {
      val path = new File(baseDir, UUID.randomUUID().toString()).toPath()
      Files.createDirectory(path)
      val result = FileWatcher.watch(path = path, fileWatcher = fileChangedListener)
      result match {
        case Right(key) => log.info(key.toString())
        case Left(e) => throw e
      }

      FileWatcher.pathsWatched match {
        case Some(paths) =>
          paths.size should be >= (1)
          log.info("watchedPaths count = {}", paths.size)
          paths.foreach(p => log.info(p.toString))
        case None => throw new IllegalStateException("Expected at least one path to be registered")
      }
    }

    it("can be queried for FileWatcherRegistration using the FileWatcherRegistrationKey that was returned when registering") {
      val path = new File(baseDir, UUID.randomUUID().toString()).toPath()
      Files.createDirectory(path)
      val result = FileWatcher.watch(path = path, fileWatcher = fileChangedListener)
      result match {
        case Right(key) => log.info(key.toString())
        case Left(e) => throw e
      }
    }
  }

}