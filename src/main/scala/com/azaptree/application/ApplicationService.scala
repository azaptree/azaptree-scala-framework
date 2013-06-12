package com.azaptree.application

import ApplicationService._
import akka.event.EventBus
import scala.concurrent.Lock
import scala.collection.immutable.VectorBuilder

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
    app = components().foldLeft(app) { (app, comp) => app.register(comp) }
  }

  def stop(): Unit = {
    app = app.shutdown()
  }

  def componentNames: Iterable[String] = components().map(_.name)

  def startedComponentNames: Iterable[String] = app.components.map(_.name)

  def stoppedComponentNames: Iterable[String] = {
    val startedCompNames = Set[String]() ++ startedComponentNames
    componentNames.filterNot(startedCompNames(_))
  }

  def stopComponent(compName: String): Option[Exception] = {
    app.shutdownComponent(compName) match {
      case Left(e) => Some(e)
      case Right(app2) =>
        app = app2
        None
    }
  }

  def startComponent(compName: String): Either[Exception, Boolean] = {
    def findByName: Component[_, _] => Boolean = _.name == compName

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

  def componentDependendents(compName: String): Option[List[String]] = {
    val comps = components()
    require(comps.find(_.name == compName).isDefined, "Invalid component name")

    Application.componentDependencies(comps.toList) match {
      case None => None
      case Some(map) =>
        val dependents = map.filter(_._2.contains(compName))
        if (dependents.isEmpty) None else Some(dependents.keys.toList)
    }
  }

  def registerComponent(comp: Component[ComponentNotConstructed, _]) = {
    require(components().find(_.name == comp.name).isEmpty, "Component with the same is already registered: " + comp.name)
    app = app.register(comp)
    registeredComponents = registeredComponents :+ comp
  }

}