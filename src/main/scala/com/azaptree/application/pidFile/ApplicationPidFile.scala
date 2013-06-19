package com.azaptree.application.pidFile

import com.azaptree.application.ApplicationExtension
import java.io.File
import com.azaptree.nio.file.FileWatcherService
import com.azaptree.utils._
import org.apache.commons.io.FileUtils
import java.nio.file.StandardWatchEventKinds._
import com.azaptree.application.ApplicationService

case class ApplicationPidFile(appName: String, watchDir: File)(implicit fileWatcherService: FileWatcherService, applicationService: ApplicationService)
    extends ApplicationExtension {
  require(!appName.trim().isEmpty(), "appName cannot be blank")

  /**
   * Creates a PID file using the following naming pattern: $appName_$HOST_$PID.pid
   */
  def pidFile = new File(watchDir, s"${appName}_${HOST}_$PID.pid")

  override def start() = {
    if (!watchDir.exists()) {
      watchDir.mkdirs()
    }

    if (!watchDir.exists()) {
      throw new IllegalStateException(s"Unable to create watchDir: $watchDir")
    }

    val f = pidFile
    FileUtils.touch(f)
    fileWatcherService.watch(f.toPath(), ENTRY_DELETE :: Nil, (watchEvent) => {
      applicationService.stop()
    })

    pidFile.deleteOnExit()
  }

  override def stop() = {
    val f = pidFile
    if (f.exists()) {
      f.delete()
    }
  }
}