package com.azaptree.application.model

case class Application(name: String)

case class ApplicationVersion(
  app: Application,
  version: String,
  dependencies: Option[Iterable[ComponentVersion]] = None)