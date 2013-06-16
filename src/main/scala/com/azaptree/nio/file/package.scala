package com.azaptree.nio

import java.nio.file.WatchEvent
package object file {

  type WatchEventProcessor = (WatchEvent[_]) => Unit
}