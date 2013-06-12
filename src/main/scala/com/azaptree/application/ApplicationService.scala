package com.azaptree.application

import ApplicationService._
import akka.event.EventBus
import scala.concurrent.Lock
import scala.collection.immutable.VectorBuilder
import org.slf4j.LoggerFactory
import scala.annotation.tailrec

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
            case None => Left(new IllegalArgumentException(s"Invalid component name: $compName"))
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

  def getComponentObjectClass(compName: String): Option[Class[_]] = {
    components().find(_.name == compName).map(_.getClass())
  }

  def getComponentObject[A](compName: String): Option[A] = {
    components().find(_.name == compName).map(_.asInstanceOf[A])
  }

  def stoppedComponentNames: Iterable[String] = {
    val startedCompNames = Set[String]() ++ startedComponentNames
    componentNames.filterNot(startedCompNames(_))
  }

  def componentDependencies(compName: String): Option[List[String]] = {
    require(components().find(_.name == compName).isDefined, "Invalid component name")

    for {
      componentDependenciesMap <- Application.componentDependencies(components().toList)
      componentDependencies <- componentDependenciesMap.get(compName)
    } yield {
      componentDependencies
    }
  }

  def componentDependents(compName: String): Option[List[String]] = {
    val comps = components()
    require(comps.find(_.name == compName).isDefined, "Invalid component name")

    Application.componentDependencies(comps) match {
      case None => None
      case Some(map) =>
        val dependents = map.filter(_._2.contains(compName))
        if (dependents.isEmpty) {
          None
        } else {
          val dependentNames = for {
            dependentName <- dependents.keys.toList
          } yield {
            val dependents = componentDependents(dependentName)
            println(s"============ $dependentName <- $dependents")
            dependents match {
              case None => dependentName :: Nil
              case Some(d) => dependentName :: d
            }
          }

          Some((Set[String]() ++ dependentNames.flatten).toList)
        }
    }
  }

}