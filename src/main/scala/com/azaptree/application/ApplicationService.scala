package com.azaptree.application

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import scala.Option.option2Iterable
import scala.concurrent.Future
import scala.concurrent.Lock
import org.slf4j.LoggerFactory
import com.azaptree.application.healthcheck.HealthCheck
import com.azaptree.application.healthcheck.HealthCheckResult
import com.azaptree.concurrent.ConfigurableThreadFactory
import akka.event.EventBus
import akka.event.japi.SubchannelEventBus
import com.azaptree.application.healthcheck.ApplicationHealthCheck
import com.azaptree.application.healthcheck.HealthCheckRunner
import com.azaptree.concurrent.OneTimeTaskSchedule
import com.azaptree.concurrent.OneTimeTask
import com.azaptree.concurrent.PeriodicTaskSchedule
import com.azaptree.concurrent.PeriodicTask
import com.azaptree.concurrent.RecurringTaskWithFixedDelay
import com.azaptree.concurrent.RecurringTaskWithFixedDelayTaskSchedule

/**
 * Application level health checks are scheduled to run right after they are added.
 *
 * Component level health checks are only scheduled to run while the component is started.
 * Once the component is stopped, the component specific scheduled health checked are cancelled.
 *
 */
class ApplicationService(asyncEventBus: Boolean = true) {

  sys.addShutdownHook(() => stop())

  private[this] lazy val log = LoggerFactory.getLogger("com.azaptree.application.ApplicationService")

  private[this] lazy val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
    ConfigurableThreadFactory(threadBaseName = Some("ApplicationService"), daemon = Some(true), uncaughtExceptionHandler = Some(new Thread.UncaughtExceptionHandler() {
      override def uncaughtException(t: Thread, e: Throwable) = {

        val threadName = t.getName()
        log.error(s"Scheduled task failed on thread: $threadName", e);
      }
    })))

  private[this] val lock = new Lock()

  @volatile
  private[this] var healthChecks: Option[List[ApplicationHealthCheck]] = None

  @volatile
  private[this] var scheduledFuturesForAppLevelHealthChecks = Vector.empty[ScheduledFuture[_]]

  private[this] val scheduledFuturesForHealthChecksLock = new Lock()

  @volatile
  private[this] var scheduledFuturesForCompLevelHealthChecks = Map.empty[String, Iterable[ScheduledFuture[_]]]

  /**
   * TODO: 2013-06-14: For some reason after re-factoring the code, SBT fails to compile the code when I try to create the application with named constructor params,
   * e.g., private[this] var app: Application = Application(evenbus = new SynchronousSubchannelEventBus()).
   *
   * It compiles fine within Eclipse, but not using sbt on the command line.
   *
   * sbt version: 0.12.4-RC2
   * scala version: 2.10.1
   *
   * NOTE: within Eclipse I am using scala version 2.10.2 - compile fails with sbt and scala 2.10.2
   *
   */
  @volatile
  private[this] var app: Application = {
    val app = if (asyncEventBus) {
      Application(Nil, new AsynchronousSubchannelEventBus())
    } else {
      Application(Nil, new SynchronousSubchannelEventBus())
    }

    val subscriber = handleComponentEvents _
    app.eventBus.subscribe(subscriber, classOf[ComponentStartedEvent])
    app.eventBus.subscribe(subscriber, classOf[ComponentShutdownEvent])

    app
  }

  private def handleComponentEvents(event: Any): Unit = {
    scheduledFuturesForHealthChecksLock.acquire()
    try {
      event match {
        case e: ComponentStartedEvent =>
          scheduledFuturesForCompLevelHealthChecks.get(e.comp.name).foreach(scheduledFutures => scheduledFutures.foreach(_.cancel(true)))

          val scheduledFutures = for {
            healthChecks <- e.comp.healthChecks
          } yield {
            healthChecks.foldLeft(List.empty[ScheduledFuture[_]])((tail, healthCheck) => {
              schedule(healthCheck._1, healthCheck._2) match {
                case Some(h) => h :: tail
                case None => tail
              }
            })
          }
          scheduledFutures.foreach { scheduledFuture =>
            scheduledFuturesForCompLevelHealthChecks += (e.comp.name -> scheduledFuture)
          }
        case e: ComponentShutdownEvent =>
          scheduledFuturesForCompLevelHealthChecks.get(e.comp.name).foreach(scheduledFutures => scheduledFutures.foreach(_.cancel(true)))
          scheduledFuturesForCompLevelHealthChecks -= e.comp.name
        case _ => log.warn(s"Received unexpected event: $event")
      }
    } finally {
      scheduledFuturesForHealthChecksLock.release()
    }
  }

  @volatile
  private[this] var components = Vector.empty[Component[ComponentNotConstructed, _]]

  def eventBus: SubchannelEventBus[Any, Any => Unit, Class[_]] = app.eventBus

  /**
   * It's ok to have multiple HealthChecks with the same name, but it is recommended to have unique names.
   */
  def addHealthCheck(healthCheck: HealthCheck, healthCheckRunner: HealthCheckRunner): Unit = {
    healthChecks match {
      case Some(h) => healthChecks = Some((healthCheck, healthCheckRunner) :: h)
      case _ => healthChecks = Some((healthCheck, healthCheckRunner) :: Nil)
    }

    scheduledFuturesForHealthChecksLock.acquire()
    try {
      schedule(healthCheck, healthCheckRunner).foreach(s => scheduledFuturesForAppLevelHealthChecks = scheduledFuturesForAppLevelHealthChecks :+ s)
    } finally {
      scheduledFuturesForHealthChecksLock.release()
    }
  }

  def cancelScheduledAppLevelHealthChecks(): Unit = {
    scheduledFuturesForHealthChecksLock.acquire()
    try {
      scheduledFuturesForAppLevelHealthChecks.foreach(_.cancel(true))
      scheduledFuturesForAppLevelHealthChecks = Vector.empty[ScheduledFuture[_]]
    } finally {
      scheduledFuturesForHealthChecksLock.release()
    }
  }

  def scheduleAppLevelHealthChecks(): Unit = {
    healthChecks.foreach { healthChecksList =>
      {
        if (healthChecksList.size != scheduledFuturesForAppLevelHealthChecks.size) {
          scheduledFuturesForHealthChecksLock.acquire()
          try {
            scheduledFuturesForAppLevelHealthChecks.foreach(_.cancel(true))
            scheduledFuturesForAppLevelHealthChecks = Vector.empty[ScheduledFuture[_]]
          } finally {
            scheduledFuturesForHealthChecksLock.release()
          }

          healthChecksList.foreach(healthCheck => schedule(healthCheck._1, healthCheck._2))
        }
      }

    }
  }

  private def schedule(healthCheck: HealthCheck, healthCheckRunner: HealthCheckRunner): Option[ScheduledFuture[_]] = {
    for {
      schedule <- healthCheck.config.schedule
    } yield {
      val task: () => Unit = () => runHealthCheck((healthCheck: HealthCheck, healthCheckRunner: HealthCheckRunner))
      val scheduledTask = schedule match {
        case c: OneTimeTaskSchedule => OneTimeTask(task, c)
        case c: PeriodicTaskSchedule => PeriodicTask(task, c)
        case c: RecurringTaskWithFixedDelayTaskSchedule => RecurringTaskWithFixedDelay(task, c)
      }
      scheduledTask.schedule(scheduledExecutorService)
    }
  }

  def startedComponentHealthChecks(): Map[String, List[ApplicationHealthCheck]] = {
    val healthChecks = for {
      comp <- app.components
      compHealthChecks <- comp.healthChecks
    } yield {
      (comp.name -> compHealthChecks)
    }

    Map[String, List[ApplicationHealthCheck]]() ++ healthChecks
  }

  def applicationHealthChecks(): Option[List[ApplicationHealthCheck]] = healthChecks

  def componentHealthChecks(compName: String): Option[List[ApplicationHealthCheck]] = {
    app.components.find(_.name == compName) match {
      case Some(c) => c.healthChecks
      case None => None
    }
  }

  def runApplicationHealthChecks(): Option[List[Future[HealthCheckResult]]] = {
    for {
      appHealthChecks <- healthChecks
    } yield {
      for {
        healthCheck <- appHealthChecks
      } yield {
        runHealthCheck(healthCheck)
      }
    }
  }

  def runApplicationHealthCheck(healthCheckName: String): Option[Future[HealthCheckResult]] = {
    healthChecks match {
      case Some(appHealthChecks) =>
        appHealthChecks.find(_._1.info.name == healthCheckName) match {
          case Some(healthCheck) => Some(runHealthCheck(healthCheck))
          case None => None
        }
      case None => None
    }
  }

  /**
   * Only runs healthchecks for components that are started
   */
  def runComponentHealthChecks(): Option[List[Future[HealthCheckResult]]] = {
    val healthCheckResults = for {
      comp <- app.components
      healthChecks <- comp.healthChecks
    } yield {
      for {
        healthCheck <- healthChecks
      } yield {
        runHealthCheck(healthCheck)
      }
    }

    val componentHealthCheckResults = healthCheckResults.flatten
    if (componentHealthCheckResults.isEmpty) None else Some(componentHealthCheckResults)
  }

  /**
   * Runs all healthchecks for comonents that have been started and all application healthchecks
   */
  def runAllHealthChecks(): Option[List[Future[HealthCheckResult]]] = {
    runApplicationHealthChecks() match {
      case Some(appHeathCheckResults) =>
        runComponentHealthChecks() match {
          case Some(compHeathCheckResults) => Some(appHeathCheckResults ++ compHeathCheckResults)
          case None => Some(appHeathCheckResults)
        }
      case None => runComponentHealthChecks()
    }
  }

  def healthChecksByGroupName(group: String): Option[List[ApplicationHealthCheck]] = {
    val appHealthChecks = healthChecks match {
      case Some(healthChecks) => healthChecks.filter(_._1.info.group == group)
      case None => Nil
    }

    val compHealthChecks = app.components.filter(_.healthChecks.isDefined).map(_.healthChecks.get).flatten.filter(_._1.info.group == group)
    val healthChecksByGroup = compHealthChecks ++ appHealthChecks
    if (healthChecksByGroup.isEmpty) None else Some(healthChecksByGroup)
  }

  /**
   * Runs all healthchecks for comonents that have been started and all application healthchecks
   */
  def runAllHealthChecksByGroup(group: String): Option[List[Future[HealthCheckResult]]] = {
    healthChecksByGroupName(group) match {
      case Some(appHeathCheckResults) =>
        val l: List[Future[HealthCheckResult]] = Nil
        Some(appHeathCheckResults.foldLeft(l)((l, hc) => runHealthCheck(hc) :: l))
      case None => None
    }
  }

  def runHealthCheck(applicationHealthCheck: ApplicationHealthCheck): Future[HealthCheckResult] = {
    applicationHealthCheck._2(applicationHealthCheck._1, this)
  }

  def runComponentHealthChecks(compName: String): Either[InvalidComponentNameException, Option[List[Future[HealthCheckResult]]]] = {
    app.components.find(_.name == compName) match {
      case Some(comp) =>
        Right(for {
          healthChecks <- comp.healthChecks
        } yield {
          for (healthCheck <- healthChecks) yield runHealthCheck(healthCheck)
        })
      case None => Left(new InvalidComponentNameException(compName))
    }
  }

  def start(): Unit = {
    lock.acquire()
    try {
      app = components.foldLeft(app) { (app, comp) =>
        app.components.find(_.name == comp.name) match {
          case None => app.register(comp)
          case _ => app
        }
      }

      scheduleAppLevelHealthChecks()
    } finally {
      lock.release()
    }
  }

  def stop(): Unit = {
    lock.acquire()
    try {
      app = app.shutdown()
      cancelScheduledAppLevelHealthChecks()
    } finally {
      lock.release()
    }
  }

  def stopComponent(compName: String): Option[Exception] = {
    lock.acquire()
    try {
      app.shutdownComponent(compName) match {
        case Left(e) => Some(e)
        case Right(app2) =>
          app = app2
          None
      }
    } finally {
      lock.release()
    }
  }

  def startComponent(compName: String): Either[Exception, Boolean] = {
    def findByName: Component[_, _] => Boolean = _.name == compName

    lock.acquire()
    try {
      app.components.find(findByName) match {
        case Some(comp) => Right(false)
        case None =>
          components.find(findByName) match {
            case Some(comp) =>
              app = app.register(comp)
              Right(true)
            case None => Left(new InvalidComponentNameException(compName))
          }
      }
    } finally {
      lock.release()
    }
  }

  def registerComponent(comp: Component[ComponentNotConstructed, _]): Unit = {
    lock.acquire()
    try {
      require(components.find(_.name == comp.name).isEmpty, "Component with the same is already registered: " + comp.name)
      app = app.register(comp)
      components = components :+ comp
    } finally {
      lock.release()
    }
  }

  def isRunning(): Boolean = !app.components.isEmpty

  def componentNames: Iterable[String] = components.map(_.name)

  def startedComponentNames: Iterable[String] = app.components.map(_.name)

  /**
   * Returns true only if a component with the specified name has been started.
   * NOTE: if the specified component name is invalid, then false will be returned as well
   * - check with startedComponentNames if it refers to a Component that has been registered with the ApplicationService
   */
  def isComponentStarted(compName: String): Boolean = startedComponentNames.find(_ == compName).isDefined

  /**
   * only returns the component object if the component is started
   */
  def getStartedComponentObjectClass(compName: String): Option[Class[_]] = {
    app.getComponentObjectClass(compName)
  }

  /**
   * only returns the component object if the component is started
   */
  def getStartedComponentObject[A](compName: String): Option[A] = {
    app.getComponentObject[A](compName)
  }

  def stoppedComponentNames: Iterable[String] = {
    val startedCompNames = Set[String]() ++ startedComponentNames
    componentNames.filterNot(startedCompNames(_))
  }

  def componentDependencies(compName: String): Either[InvalidComponentNameException, Option[List[String]]] = {
    components.find(_.name == compName) match {
      case None => Left(new InvalidComponentNameException(compName))
      case _ =>
        val compDependencies = for {
          componentDependenciesMap <- Application.componentDependencies(components.toList)
          componentDependencies <- componentDependenciesMap.get(compName)
        } yield {
          componentDependencies
        }

        Right(compDependencies)
    }

  }

  def componentDependents(compName: String): Either[InvalidComponentNameException, Option[List[String]]] = {
    components.find(_.name == compName) match {
      case None => Left(new InvalidComponentNameException(compName))
      case _ =>
        Application.componentDependencies(components.toList) match {
          case None => Right(None)
          case Some(map) =>
            val dependents = map.filter(_._2.contains(compName))
            if (dependents.isEmpty) {
              Right(None)
            } else {
              val dependentNames = for {
                dependentName <- dependents.keys.toList
              } yield {
                val dependents = componentDependents(dependentName)
                dependents match {
                  case Right(None) => dependentName :: Nil
                  case Right(Some(d)) => dependentName :: d
                  case Left => Nil
                }
              }

              Right(Some((Set[String]() ++ dependentNames.flatten).toList))
            }
        }
    }

  }

}

class InvalidComponentNameException(compName: String) extends IllegalArgumentException(compName)
class DuplicateComponentNameException(compName: String) extends IllegalArgumentException(compName)