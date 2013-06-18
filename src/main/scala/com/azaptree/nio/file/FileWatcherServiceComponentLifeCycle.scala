package com.azaptree.nio.file

import com.azaptree.application.ComponentLifeCycle
import com.azaptree.application.Component
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.application.ComponentConstructed
import com.azaptree.application.ComponentStopped
import com.azaptree.application.ComponentStarted

class FileWatcherServiceComponentLifeCycle extends ComponentLifeCycle[FileWatcherService] {

  override def create(comp: Component[ComponentNotConstructed, FileWatcherService]): Component[ComponentConstructed, FileWatcherService] = {
    val service = new FileWatcherService {}
    comp.copy[ComponentConstructed, FileWatcherService](componentObject = Some(service))
  }

  override def stop(comp: Component[ComponentStarted, FileWatcherService]): Component[ComponentStopped, FileWatcherService] = {
    comp.componentObject.foreach(_.destroy())
    comp.copy[ComponentStopped, FileWatcherService](componentObject = None)
  }

}