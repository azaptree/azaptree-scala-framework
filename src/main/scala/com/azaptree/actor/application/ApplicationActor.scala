package com.azaptree.actor.application

import com.azaptree.actor.message.Message
import com.typesafe.config.Config
import scala.collection.immutable.Nil
import java.lang.management.ManagementFactory
import scala.collection.convert.Wrappers
import com.azaptree.actor.message.MessageProcessor

class ApplicationActor extends MessageProcessor {
  import ApplicationActor._

  override def receiveMessage(): PartialFunction[Message[_], Unit] = {
    case Message(GetApplicationInfo, _) => sender ! Message(applicationInfo)
    case Message(req: GetJvmInfo, _) => sender ! Message(jvmInfo(req.infoTypes))
  }

  def applicationInfo: ApplicationInfo = {
    actorConfig.config match {
      case Some(c: Config) =>
        ApplicationInfo(
          appName = c.getString(ConfigKeys.APP_NAME),
          c.getString(ConfigKeys.APP_VERSION))
      case None => ApplicationInfo()
    }
  }

  def jvmInfo(infoTypes: Seq[JvmInfoRequest]): Seq[JvmInfo] = {
    def jvmInfo(infoType: JvmInfoRequest): JvmInfo = {
      infoType match {
        case ClassLoadingInfoRequest =>
          val classLoadingMbean = ManagementFactory.getClassLoadingMXBean()
          ClassLoadingInfo(
            loadedClassCount = classLoadingMbean.getLoadedClassCount(),
            totalLoadedClassCount = classLoadingMbean.getTotalLoadedClassCount(),
            unloadedClassCount = classLoadingMbean.getUnloadedClassCount())

        case RuntimeInfoRequest =>
          val runtimeMBean = ManagementFactory.getRuntimeMXBean()
          RuntimeInfo(
            bootClassPath = runtimeMBean.getBootClassPath(),
            classPath = runtimeMBean.getClassPath(),
            inputArguments =
              {
                val inputArgs = runtimeMBean.getInputArguments()
                if (inputArgs.isEmpty()) {
                  None
                } else {
                  Some(Wrappers.JListWrapper(inputArgs).toSeq)
                }
              },
            javaLibraryPath = runtimeMBean.getLibraryPath(),
            jvmName = runtimeMBean.getName(),
            jvmSpecName = runtimeMBean.getSpecName(),
            jvmSpecVendor = runtimeMBean.getSpecVendor(),
            jvmSpecVersion = runtimeMBean.getSpecVersion(),
            startTime = runtimeMBean.getStartTime(),
            uptime = runtimeMBean.getUptime(),
            jvmImplementationName = runtimeMBean.getVmName(),
            jvmImplementationVendor = runtimeMBean.getVmVendor(),
            jvmImplementationVersion = runtimeMBean.getVmVersion(),
            bootClassPathSupported = runtimeMBean.isBootClassPathSupported())
      }
    }

    infoTypes.foldLeft(Vector[JvmInfo]())((resultList, infoType) => {
      resultList :+ jvmInfo(infoType)
    })

  }

}

object ApplicationActor {
  object ConfigKeys {
    val APP = "app"
    val APP_NAME = s"$APP.name"
    val APP_VERSION = s"$APP.version"
  }

  sealed trait ApplicationMessage

  sealed trait ApplicationRequest extends ApplicationMessage

  case object GetApplicationInfo extends ApplicationRequest

  case class GetJvmInfo(infoTypes: Seq[JvmInfoRequest]) extends ApplicationRequest

  ////////////////////////////////////////////////////
  sealed trait ApplicationResponse extends ApplicationMessage

  case class ApplicationInfo(appName: String = "", appVersion: String = "") extends ApplicationResponse

  ////////////////////////////////////////////////////
  sealed trait JvmInfoRequest

  case object ClassLoadingInfoRequest extends JvmInfoRequest

  case object RuntimeInfoRequest extends JvmInfoRequest

  sealed trait JvmInfo

  case class ClassLoadingInfo(loadedClassCount: Int, totalLoadedClassCount: Long, unloadedClassCount: Long) extends JvmInfo

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