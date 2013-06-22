package com.azaptree.application.model

case class Component(name: String)

case class ComponentVersion(
  comp: Component,
  version: String,
  compDependencies: Option[Iterable[ComponentVersion]] = None)