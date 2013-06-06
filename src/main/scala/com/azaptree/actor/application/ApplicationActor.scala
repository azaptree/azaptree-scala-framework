package com.azaptree.actor.application

import com.azaptree.actor.message.Message
import com.azaptree.actor.message.MessageActor

class ApplicationActor extends MessageActor {
  import ApplicationActor._

  override def receiveMessage(): PartialFunction[Message[_], Unit] = {
    case Message(GetApplicationInfo, _) =>

  }

}

object ApplicationActor {
  sealed trait ApplicationMessage

  sealed trait ApplicationRequest extends ApplicationMessage

  case object GetApplicationInfo extends ApplicationRequest

  case class GetJvmInfo(infoTypes: Seq[JvmInfoRequest]) extends ApplicationRequest

  ////////////////////////////////////////////////////
  sealed trait ApplicationResponse extends ApplicationMessage

  case class ApplicationInfo(appName: String, appVersion: String) extends ApplicationResponse

  ////////////////////////////////////////////////////
  sealed trait JvmInfoRequest

  case object ClassLoadingInfoRequest extends JvmInfoRequest

  case object RuntimeInfoRequest extends JvmInfoRequest

  sealed trait JvmInfo

  case class ClassLoadingInfo(loadedClassCount: Int, totalLoadedClassCount: Int, unloadedClassCount: Int) extends JvmInfo

  case class RuntimeInfo(
    bootClassPath: String,
    classPath: String,
    inputArguments: Option[Seq[String]] = None,
    javaLibraryPath: String,
    jvmName: String,
    jvmSpecName: String,
    jvmSpecVendor: String,
    jvmSpecVersion: String,
    startTime: Long,
    uptime: Long,
    jvmImplementationName: String,
    jvmImplementationVendor: String,
    jvmImplementationVersion: String,
    bootClassPathSupported: Boolean) extends JvmInfo

}