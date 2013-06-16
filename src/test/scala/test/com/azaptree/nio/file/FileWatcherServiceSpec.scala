package test.com.azaptree.nio.file

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.nio.file.FileWatcherService

object FileWatcher extends FileWatcherService

class FileWatcherServiceSpec extends FunSpec with ShouldMatchers {

  describe("A FileWatcherService") {
    it("can be used to register file paths to watch") {
      pending
    }

    it("can cancel a FileWatcherRegistration") {
      pending
    }

    it("can notify registered listeners when a file path watch event has occurred") {
      pending
    }

    it("can be used to query which file paths are being watched") {
      pending
    }

    it("can be queried for FileWatcherRegistration using the FileWatcherRegistrationKey that was returned when registering") {
      pending
    }
  }

}