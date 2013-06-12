package com.azaptree.application

import scala.annotation.tailrec

import org.slf4j.LoggerFactory

import Application._
import akka.event.EventBus
import akka.event.japi.LookupEventBus
import akka.event.japi.SubchannelEventBus

object Application {
  def componentDependencies(components: List[Component[_, _]]): Option[Map[String, List[String]]] = {
    var dependencyMap = Map[String, List[String]]()
    components.foreach { comp =>
      comp.dependsOn match {
        case Some(compDependencies) =>
          val compDependencyNames = compDependencies.map(_.name)
          dependencyMap += (comp.name -> compDependencyNames.toList)
        case None =>
      }
      val dependencies = dependencyMap.getOrElse(comp.name, Nil)
    }

    if (dependencyMap.isEmpty) None else Some(dependencyMap)
  }
}

case class Application(components: List[Component[ComponentStarted, _]] = Nil, eventBus: SubchannelEventBus[Any, Any => Unit, Class[_]] = new SynchronousSubchannelEventBus()) extends EventBus {

  val componentMap: Map[String, Component[ComponentStarted, _]] = {
    val componentMapEntries = components.map(c => (c.name, c)).toArray
    Map[String, Component[ComponentStarted, _]](componentMapEntries: _*)
  }

  type Event = Any
  type Subscriber = Any => Unit
  type Classifier = Class[_]

  override def publish(event: Event): Unit = eventBus.publish(event)

  override def subscribe(subscriber: Subscriber, to: Classifier): Boolean = eventBus.subscribe(subscriber, to)

  override def unsubscribe(subscriber: Subscriber): Unit = eventBus.unsubscribe(subscriber)

  override def unsubscribe(subscriber: Subscriber, from: Classifier): Boolean = eventBus.unsubscribe(subscriber, from)

  def getComponentObject[A](name: String): Option[A] = {
    componentMap.get(name) match {
      case Some(obj) => Some[A](obj.asInstanceOf[A])
      case None => None
    }
  }

  def getComponentObjectClass(name: String): Option[Class[_]] = {
    componentMap.get(name) match {
      case Some(obj) => Some(obj.getClass())
      case None => None
    }
  }

  def register(comp: Component[ComponentNotConstructed, _]): Application = {
    assert(componentMap.get(comp.name).isEmpty, "A component with the same name is already registered: " + comp.name)

    val compStarted = comp.startup()
    val appWithNewComp = copy(components = compStarted :: components)
    publish(ComponentStartedEvent(appWithNewComp, compStarted))
    appWithNewComp
  }

  def shutdownComponent(componentName: String): Either[Exception, Application] = {
    val componentToShutdown = componentMap.get(componentName)
    if (componentToShutdown.isEmpty) {
      Left(new ComponentNotFoundException(componentName))
    } else {
      try {
        val compStopped = componentToShutdown.get.shutdown()
        val appWithCompRemoved = copy(components = components.filterNot(c => c.name == componentName))
        publish(ComponentShutdownEvent(appWithCompRemoved, compStopped))
        Right(appWithCompRemoved)
      } catch {
        case e: Exception =>
          publish(ComponentShutdownFailedEvent(this, componentToShutdown.get, e))
          Left(e)
      }
    }
  }

  def shutdown(): Application = {
    publish(PreApplicationShutdownEvent(this))
    val log = LoggerFactory.getLogger(getClass())

    @tailrec
    def shutdownComponentsWithoutDependents(app: Application, components: Iterable[Component[ComponentStarted, _]], componentDependencies: Map[String, List[String]]): Application = {
      if (!components.isEmpty) {
        val dependencySet = componentDependencies.values.foldLeft(Set[String]())((set, compName) => set ++ compName)
        log.debug("dependencySet = {}", dependencySet)

        val findCompWithoutDependents: Component[ComponentStarted, _] => Boolean = c => !dependencySet.contains(c.name)
        val compsWithoutDependents = components.filter(findCompWithoutDependents)

        if (log.isDebugEnabled()) {
          log.debug("compsWithoutDependents = {}", compsWithoutDependents.mkString("\n", "\n\n", "\n"))
        }

        var componentDependenciesTemp = componentDependencies
        val appAfterCompShutdown = compsWithoutDependents.foldLeft(app) { (app, comp) =>
          val appAfterCompShutdown = app.shutdownComponent(comp.name) match {
            case Left(_) => app
            case Right(application) => application
          }
          componentDependenciesTemp -= comp.name
          appAfterCompShutdown
        }

        val compsWithDependents = components.filterNot(findCompWithoutDependents)
        shutdownComponentsWithoutDependents(appAfterCompShutdown, compsWithDependents, componentDependenciesTemp)
      } else {
        return copy(components = Nil)
      }
    }

    val compDependencyMap = componentDependencies(components)

    if (log.isDebugEnabled()) {
      log.debug("componentDependencies = {}", compDependencyMap)
      log.debug("components = {}", components.mkString("\n", "\n\n", "\n"))
    }

    val app = compDependencyMap match {
      case Some(map) => shutdownComponentsWithoutDependents(this, components, map)
      case None => shutdownComponentsWithoutDependents(this, components, Map[String, List[String]]())
    }

    publish(PostApplicationShutdownEvent(this))
    app
  }

}

class ComponentNotFoundException(componentName: String) extends IllegalArgumentException(componentName)

