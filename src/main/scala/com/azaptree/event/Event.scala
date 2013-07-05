package com.azaptree.event

import com.azaptree.application.model.ApplicationInstanceId
import java.util.UUID
import org.bson.types.ObjectId

/**
 * <pre>
 * - namespace - used to group types of events
 * - the purpose for attributes is to provide a mechanism to attach additional meta-data that can be searchable
 * - parentEventId : used in order relate events, for example a workflow may log an event that is has started.
 *                   Then events logged within the workflow would reference the parent event.
 *
 * e.g.
 *
 *   val appStartedEvent = Event(
 *   	namespace = "com.azaptree.application.ApplicationLauncher",
 *   	name = "APP_STARTED",
 *   	level = INFO,
 *   	processInfo = processInfo(),
 *   	applicationInstanceId = applicationInstanceId())
 *
 *   eventService.log(appStartedEvent)
 *
 *   val appStoppedEvent = Event(
 *   	namespace = "com.azaptree.application.ApplicationLauncher",
 *   	name = "APP_STOPPED",
 *   	level = INFO,
 *   	processInfo = processInfo(),
 *   	applicationInstanceId = applicationInstanceId(),
 *   	parentEventId = appStartedEvent.eventId)
 *
 *    eventService.log(appStartedEvent)
 *
 * </pre>
 */
case class Event(
    eventId: ObjectId = new ObjectId(),
    namespace: String,
    name: String,
    level: EventLevel,
    processInfo: ProcessInfo,
    message: Option[String] = None,
    exceptionInfo: Option[ExceptionInfo] = None,
    applictionInstanceId: ApplicationInstanceId,
    parentEventId: Option[UUID] = None,
    attributes: Option[Map[String, Any]] = None) {
}

sealed trait EventLevel

case object DEBUG extends EventLevel
case object INFO extends EventLevel
case object WARN extends EventLevel
case object ERROR extends EventLevel

case class ProcessInfo(host: String, pid: Long, thread: String, stackTrace: Option[Iterable[StackTraceElement]] = None)

case class ExceptionInfo(className: String, message: String, stackTrace: String)