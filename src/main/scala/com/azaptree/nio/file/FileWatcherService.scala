package com.azaptree.nio.file

import java.nio.file._
import java.util.UUID
import scala.reflect.ClassTag
import scala.concurrent.Lock
import com.azaptree.utils._

trait FileWatcherService {

  protected var watchKeys: Map[Path, WatchKey] = Map.empty[Path, WatchKey]

  protected var fileWatcherRegistrations: Map[Path, Vector[FileWatcherRegistration]] = Map.empty[Path, Vector[FileWatcherRegistration]]

  protected lazy val watchService: WatchService = FileSystems.getDefault().newWatchService()

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

  def pathsWatched(): Option[List[Path]]

  def fileWatcherRegistration(key: FileWatcherRegistrationKey): FileWatcherRegistration

}

case class FileWatcherRegistration(
  id: UUID = UUID.randomUUID(),
  createdOn: Long = System.currentTimeMillis(),
  path: Path,
  eventKinds: Option[List[WatchEvent.Kind[_]]] = None,
  fileWatcher: WatchEventProcessor)

case class FileWatcherRegistrationKey(id: UUID, path: Path)