package com.azaptree.application.model

case class ApplicationId(group: String, name: String)

case class ApplicationVersionId(appId: ApplicationId, version: String)

case class ApplicationVersion(id: ApplicationVersionId, dependencies: Option[Iterable[ComponentDependency]] = None)

case class ApplicationInstanceId(versionId: ApplicationVersionId, instance: String) {
  def id: String = s"${versionId.appId.group}_${versionId.appId.name}_${versionId.version}_${instance}"
}