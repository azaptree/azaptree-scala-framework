package com.azaptree.application

import scala.language.existentials

trait ApplicationEvent

case class ComponentRegistered(app: Application, comp: Component[ComponentStarted, _]) extends ApplicationEvent

case class ComponentShutdown(app: Application, comp: Component[ComponentStopped, _]) extends ApplicationEvent

case class ComponentShutdownFailed(app: Application, comp: Component[ComponentStarted, _], exception: Exception) extends ApplicationEvent

case class BeforeApplicationShutdown(app: Application) extends ApplicationEvent

case class AfterApplicationShutdown(app: Application) extends ApplicationEvent