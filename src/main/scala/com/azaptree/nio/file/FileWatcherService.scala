package com.azaptree.nio.file

import java.nio.file._
import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Lock
import scala.reflect.ClassTag

import org.slf4j.LoggerFactory

trait FileWatcherService {
  protected val log = LoggerFactory.getLogger("FileWatcherService." + getClass().getSimpleName())

  protected var watchKeys: Map[Path, WatchKey] = Map.empty[Path, WatchKey]

  protected var fileWatcherRegistrations: Map[Path, Vector[FileWatcherRegistration]] = Map.empty[Path, Vector[FileWatcherRegistration]]

  protected val watchService: WatchService = FileSystems.getDefault().newWatchService()

  private val watcherThread: Thread = new Thread(new Runnable() {
    def processEvents(key: WatchKey, registrations: Vector[FileWatcherRegistration]): Unit = {
      import scala.collection.JavaConversions._
      key.pollEvents().foreach { watchEvent =>
        if (log.isDebugEnabled()) {
          log.debug("watchEvent {context=%s, count=%s, eventKind=%s}".format(watchEvent.context(), watchEvent.count(), watchEvent.kind().name()))
        }

        watchEvent.context() match {
          case watchEventContext: Path =>
            if (log.isDebugEnabled()) {
              val watchedPath = key.watchable()
              registrations.foreach(r => log.debug(s"$watchedPath -> $r"))
            }
            registrations.filter(_.matches(watchEvent)).foreach { fileWatcherRegistration =>
              fileWatcherRegistration.fileWatcher(watchEvent)
            }

          case _ => log.warn("Received unexpected watch event type : {}", watchEvent)
        }
      }
    }

    def processEvents(key: WatchKey): Unit = {
      log.debug("Processing events for {}", key.watchable())

      key.watchable() match {
        case watchedPath: Path =>
          fileWatcherRegistrations.get(watchedPath) match {
            case Some(registrations) => processEvents(key, registrations)
            case None =>
              log.warn("Received event for a path that is not being watched: {}", watchedPath)
              key.pollEvents()
          }
        case _ => log.warn("Received unexpected watchable", key.watchable())
      }
    }

    def run() {
      log.debug("WatchService thread is running")
      while (true) {
        try {
          log.debug("Waiting for WatchKeys ...")
          val key = watchService.take()
          try {
            processEvents(key)
          } finally {
            key.reset()
          }
        } catch {
          case e: InterruptedException => throw e
          case e: Exception =>
            log.error("Error occurred while running watcher", e)
        }
      }
    }
  })

  initFileWatcherService()

  private def initFileWatcherService() {
    watcherThread.start()
    log.debug("WatchService thread has been launched")
  }

  def destroy(): Unit = {
    watcherThread.interrupt()
  }

  import StandardWatchEventKinds._

  def watch(path: Path, eventKinds: List[WatchEvent.Kind[_]] = ENTRY_CREATE :: ENTRY_DELETE :: ENTRY_MODIFY :: Nil, fileWatcher: WatchEventProcessor): Either[Exception, FileWatcherRegistrationKey] = {
    synchronized {
      try {
        val watchKey = watchKeys.get(path) match {
          case Some(watchKey) => watchKey
          case None =>
            val watchKey = path.register(watchService, eventKinds.toArray(ClassTag(classOf[WatchEvent.Kind[_]])): _*)
            watchKeys += (path -> watchKey)
            watchKey
        }

        val fileWatcherRegistration = FileWatcherRegistration(path = path, eventKinds = eventKinds, fileWatcher = fileWatcher)
        fileWatcherRegistrations.get(path) match {
          case Some(registrations) => fileWatcherRegistrations += (path -> (registrations :+ fileWatcherRegistration))
          case None => fileWatcherRegistrations += (path -> Vector(fileWatcherRegistration))
        }

        Right(FileWatcherRegistrationKey(fileWatcherRegistration.id, path))
      } catch {
        case e: Exception => Left(e)
      }
    }
  }

  def cancel(key: FileWatcherRegistrationKey): Option[FileWatcherRegistration] = {
    synchronized[Option[FileWatcherRegistration]] {
      for {
        registrations <- fileWatcherRegistrations.get(key.path)
        cancelledRegistration <- registrations.find(_.id == key.id)
      } yield {
        watchKeys(key.path).cancel()
        if (registrations.size == 1) {
          fileWatcherRegistrations -= key.path
          watchKeys -= key.path
        }
        cancelledRegistration
      }
    }

  }

  def pathsWatched(): Option[Set[Path]] = if (watchKeys.isEmpty) None else Some(watchKeys.keySet)

  def fileWatcherRegistration(key: FileWatcherRegistrationKey): Option[FileWatcherRegistration] = {
    fileWatcherRegistrations.get(key.path) match {
      case None => None
      case Some(registrations) => registrations.find(_.id == key.id)
    }
  }

}

case class FileWatcherRegistration(
    id: UUID = UUID.randomUUID(),
    createdOn: Long = System.currentTimeMillis(),
    path: Path,
    eventKinds: List[WatchEvent.Kind[_]],
    fileWatcher: WatchEventProcessor) {

  def matches(watchEvent: WatchEvent[_]): Boolean = {
    eventKinds.find(_.name() == watchEvent.kind().name()).isDefined
  }
}

case class FileWatcherRegistrationKey(id: UUID, path: Path)