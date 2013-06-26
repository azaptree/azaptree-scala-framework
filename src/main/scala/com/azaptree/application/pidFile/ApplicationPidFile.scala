package com.azaptree.application.pidFile

import com.azaptree.application.ApplicationExtension
import java.io.File
import com.azaptree.nio.file.FileWatcherService
import com.azaptree.utils._
import org.apache.commons.io.FileUtils
import java.nio.file.StandardWatchEventKinds._
import com.azaptree.application.ApplicationService
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds._
import com.azaptree.application.deployment.ApplicationDeployment

object ApplicationPidFile {
  def apply(applicationDeployment: ApplicationDeployment)(implicit fileWatcherService: FileWatcherService, applicationService: ApplicationService) = {
    val watchDir = new File(applicationDeployment.baseDir, "pid")
    new ApplicationPidFile(applicationDeployment.applicationInstanceId.id, watchDir)
  }

  def apply(appName: String, watchDir: File)(implicit fileWatcherService: FileWatcherService, applicationService: ApplicationService) = {
    new ApplicationPidFile(appName, watchDir)
  }
}

/**
 * Creates a PID file in the specified watch dir. When the pid file is deleted it will stop the ApplicationService.
 */
class ApplicationPidFile(appName: String, watchDir: File)(implicit fileWatcherService: FileWatcherService, applicationService: ApplicationService)
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

    val log = LoggerFactory.getLogger("ApplicationPidFile")
    val f = pidFile
    FileUtils.touch(f)
    log.info("Created  PID file : {}", f.getAbsolutePath())
    fileWatcherService.watch(watchDir.toPath(), ENTRY_DELETE :: Nil, (watchEvent) => {
      watchEvent.context() match {
        case p: Path =>
          log.info("Received WatchEvent for : %s -> %s".format(p, watchEvent.kind().name()))
          if (p.getFileName().toString() == f.getName() && watchEvent.kind() == ENTRY_DELETE) {
            log.info("PID file has been deleted, which is a trigger to stop the application : {}", f)
            applicationService.stop()
          }
        case _ =>
      }

    })

    pidFile.deleteOnExit()
  }

  override def stop() = { /*no action needed*/ }
}