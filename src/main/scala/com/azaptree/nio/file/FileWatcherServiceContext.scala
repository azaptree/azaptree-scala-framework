package com.azaptree.nio.file

trait FileWatcherServiceContext {
  def fileWatcherService(): FileWatcherService
}