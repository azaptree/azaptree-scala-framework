package com.azaptree.application.model

case class ApplicationId(group: String, name: String)

case class ApplicationVersionId(appId: ApplicationId, version: String)

case class ApplicationVersion(id: ApplicationVersionId, dependencies: Option[Iterable[ComponentVersionId]] = None)