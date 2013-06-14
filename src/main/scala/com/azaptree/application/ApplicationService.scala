package com.azaptree.application

import ApplicationService._
import akka.event.EventBus
import scala.concurrent.Lock
import scala.collection.immutable.VectorBuilder
import org.slf4j.LoggerFactory
import scala.annotation.tailrec
import com.azaptree.application.healthcheck._
import scala.concurrent.Future

object ApplicationService {
  type ComponentCreator = () => List[Component[ComponentNotConstructed, _]]

  sealed trait ApplicationState

  case object Started extends ApplicationState
  case object Stopped extends ApplicationState

}

class ApplicationService(val compCreator: ComponentCreator, asyncEventBus: Boolean = true) extends EventBus {
  type Event = Any
  type Subscriber = Any => Unit
  type Classifier = Class[_]

  validate()

  private[this] def validate() = {
    def duplicateNames: Iterable[String] = {
      compCreator().groupBy(_.name).filter(_._2.size > 1).keys
    }

    assert({
      val compNames = compCreator().map(_.name)
      !compNames.isEmpty && compNames.size == (Set[String]() ++ (compNames)).size
    }, s"Components must have unique names. $duplicateNames")
  }

  @volatile
  private[this] var app: Application = if (asyncEventBus) {
    Application(eventBus = new AsynchronousSubchannelEventBus())
  } else {
    Application(eventBus = new SynchronousSubchannelEventBus())
  }

  @volatile
  private[this] var registeredComponents: Vector[Component[ComponentNotConstructed, _]] = Vector.empty[Component[ComponentNotConstructed, _]]

  private[this] val initialComponents = compCreator()

  private[this] val components: ComponentCreator = () => { initialComponents ++ registeredComponents }

  override def publish(event: Event): Unit = app.publish(event)

  override def subscribe(subscriber: Subscriber, to: Classifier): Boolean = app.subscribe(subscriber, to)

  override def unsubscribe(subscriber: Subscriber): Unit = app.unsubscribe(subscriber)

  override def unsubscribe(subscriber: Subscriber, from: Classifier): Boolean = app.unsubscribe(subscriber, from)

  private[this] val lock = new Lock()

  @volatile
  private[this] var healthChecks: Option[List[ApplicationHealthCheck]] = None

  /**
   * It's ok to have multiple HealthChecks with the same name, but it is recommended to have unique names.
   */
  def addHealthCheck(healthCheck: HealthCheck, healthCheckRunner: HealthCheckRunner) = {
    healthChecks match {
      case Some(h) => healthChecks = Some((healthCheck, healthCheckRunner) :: h)
      case _ => healthChecks = Some((healthCheck, healthCheckRunner) :: Nil)
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
      app = components().foldLeft(app) { (app, comp) =>
        app.components.find(_.name == comp.name) match {
          case None => app.register(comp)
          case _ => app
        }
      }
    } finally {
      lock.release()
    }
  }

  def stop(): Unit = {
    lock.acquire()
    try {
      app = app.shutdown()
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
          components().find(findByName) match {
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

  def registerComponent(comp: Component[ComponentNotConstructed, _]) = {
    lock.acquire()
    try {
      require(components().find(_.name == comp.name).isEmpty, "Component with the same is already registered: " + comp.name)
      registeredComponents = registeredComponents :+ comp
    } finally {
      lock.release()
    }
  }

  def isRunning(): Boolean = !app.components.isEmpty

  def componentNames: Iterable[String] = components().map(_.name)

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
    components().find(_.name == compName) match {
      case None => Left(new InvalidComponentNameException(compName))
      case _ =>
        val compDependencies = for {
          componentDependenciesMap <- Application.componentDependencies(components().toList)
          componentDependencies <- componentDependenciesMap.get(compName)
        } yield {
          componentDependencies
        }

        Right(compDependencies)
    }

  }

  def componentDependents(compName: String): Either[InvalidComponentNameException, Option[List[String]]] = {
    val comps = components()
    comps.find(_.name == compName) match {
      case None => Left(new InvalidComponentNameException(compName))
      case _ =>
        Application.componentDependencies(comps) match {
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