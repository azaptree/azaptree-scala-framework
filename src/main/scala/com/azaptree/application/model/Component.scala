package com.azaptree.application.model

case class ComponentId(group: String, name: String)

case class ComponentVersionId(compId: ComponentId, version: String)

case class ComponentVersion(id: ComponentVersionId, compDependencies: Option[Iterable[ComponentDependency]] = None) {

  def compDependency(compId: ComponentId): Option[ComponentVersionId] = {
    compDependencies match {
      case None => None
      case Some(versionIds) =>
        for {
          componentDependency <- versionIds.find(_.compVersionId.compId == compId)
        } yield {
          componentDependency.compVersionId
        }
    }
  }
}

case class ComponentDependency(compVersionId: ComponentVersionId, configs: Option[Iterable[ComponentDependencyConfig]] = None)

case class ComponentDependencyConfig(name: String, attributes: Option[Map[String, String]] = None)

case class ComponentInstanceId(versionId: ComponentVersionId, instance: String)