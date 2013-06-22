package com.azaptree.application.model

case class ComponentId(group: String, name: String)

case class ComponentVersionId(compId: ComponentId, version: String)

case class ComponentVersion(id: ComponentVersionId, compDependencies: Option[Iterable[ComponentVersion]] = None)