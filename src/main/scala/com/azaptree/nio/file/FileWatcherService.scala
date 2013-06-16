package com.azaptree.nio.file

import java.nio.file._
import java.util.UUID
import scala.reflect.ClassTag
import scala.concurrent.Lock
import com.azaptree.utils._
import scala.concurrent.ExecutionContext
import org.slf4j.LoggerFactory

trait FileWatcherService {

  protected var watchKeys: Map[Path, WatchKey] = Map.empty[Path, WatchKey]

  protected var fileWatcherRegistrations: Map[Path, Vector[FileWatcherRegistration]] = Map.empty[Path, Vector[FileWatcherRegistration]]

  protected val watchService: WatchService = FileSystems.getDefault().newWatchService()

  ExecutionContext.Implicits.global.execute(new Runnable() {

    def run() {
      val log = LoggerFactory.getLogger("com.azaptree.nio.file.FileWatcherService")

      def processEvents(key: WatchKey) = {
        import scala.collection.JavaConversions._
        key.pollEvents().foreach { watchEvent =>
          watchEvent.context() match {
            case p: Path =>
              for {
                fileWatcherRegistrations <- fileWatcherRegistrations.get(p)
              } yield {
                fileWatcherRegistrations.filter(_.matches(watchEvent)).foreach { fileWatcherRegistration =>
                  fileWatcherRegistration.fileWatcher(watchEvent)
                }
              }
            case _ => log.warn("Received unexpected watch event type : {}", watchEvent)
          }
        }
      }

      while (true) {
        try {
          val key = watchService.take()
          try {
            processEvents(key)
          } finally {
            key.reset()
          }
        } catch {
          case e: Exception =>
            log.error("Error occurred while running watcher", e)
        }
      }
    }
  })

  def watch(path: Path, eventKinds: Option[List[WatchEvent.Kind[_]]], fileWatcher: WatchEventProcessor): FileWatcherRegistrationKey = {
    synchronized {
      val watchKey = watchKeys.get(path) match {
        case Some(watchKey) => watchKey
        case None =>
          eventKinds match {
            case None => path.register(watchService)
            case Some(watchEventKinds) =>
              val watchKey = path.register(watchService, watchEventKinds.toArray(ClassTag(classOf[FileWatcherRegistration])): _*)
              watchKeys += (path -> watchKey)
              watchKey
          }
      }

      val fileWatcherRegistration = FileWatcherRegistration(path = path, eventKinds = eventKinds, fileWatcher = fileWatcher)
      fileWatcherRegistrations.get(path) match {
        case Some(registrations) => fileWatcherRegistrations += (path -> (registrations :+ fileWatcherRegistration))
        case None => fileWatcherRegistrations += (path -> Vector(fileWatcherRegistration))
      }

      FileWatcherRegistrationKey(fileWatcherRegistration.id, path)
    }
  }

  def cancel(key: FileWatcherRegistrationKey): Unit = {
    synchronized { () =>
      fileWatcherRegistrations.get(key.path).foreach { registrations =>
        val updatedRegistrations = registrations.filterNot(_.id == key.id)
        if (updatedRegistrations.isEmpty) {
          fileWatcherRegistrations -= key.path
          watchKeys(key.path).cancel()
          watchKeys -= key.path
        }
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
  eventKinds: Option[List[WatchEvent.Kind[_]]] = None,
  fileWatcher: WatchEventProcessor) {

  def matches(watchEvent: WatchEvent[_]): Boolean = {
    eventKinds match {
      case None => true
      case Some(eventKinds) => eventKinds.find(_.name() == watchEvent.kind().name()).isDefined
    }
  }
}

case class FileWatcherRegistrationKey(id: UUID, path: Path)