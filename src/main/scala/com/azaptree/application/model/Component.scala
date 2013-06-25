package com.azaptree.application.model

case class ComponentId(group: String, name: String)

case class ComponentVersionId(compId: ComponentId, version: String)

case class ComponentVersion(id: ComponentVersionId, compDependencies: Option[Iterable[ComponentVersionId]] = None) {

  def compDependency(compId: ComponentId): Option[ComponentVersionId] = {
    compDependencies match {
      case None => None
      case Some(versionIds) => versionIds.find(_.compId == compId)
    }
  }
}