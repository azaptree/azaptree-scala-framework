package com.azaptree.application

import scala.annotation.tailrec
import org.slf4j.LoggerFactory
import akka.event.LookupClassification
import akka.event.EventBus

case class Application(components: List[Component[ComponentStarted, _]] = Nil) extends ApplicationEventBus {
  def register(comp: Component[ComponentNotConstructed, _]): Application = {
    assert(components.find(_.name == comp.name).isEmpty, "A component with the same name is already registered: " + comp.name)

    val compStarted = comp.startup()
    val appWithNewComp = copy(components = compStarted :: components)
    publish(ComponentRegistered(appWithNewComp, compStarted))
    appWithNewComp
  }

  def shutdownComponent(componentName: String): Either[Exception, Application] = {
    val containsComp: Component[ComponentStarted, _] => Boolean = c => c.name == componentName
    val componentToShutdown = components.find(containsComp)
    if (componentToShutdown.isEmpty) {
      Left(new ComponentNotFoundException(componentName))
    } else {
      try {
        val compStopped = componentToShutdown.get.shutdown()
        val appWithCompRemoved = copy(components = components.filterNot(containsComp))
        publish(ComponentShutdown(appWithCompRemoved, compStopped))
        Right(appWithCompRemoved)
      } catch {
        case e: Exception =>
          publish(ComponentShutdownFailed(this, componentToShutdown.get, e))
          Left(e)
      }
    }
  }

  def shutdown(): Application = {
    publish(BeforeApplicationShutdown(this))
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

    def componentDependencies(): Option[Map[String, List[String]]] = {
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

    val compDependencyMap = componentDependencies

    if (log.isDebugEnabled()) {
      log.debug("componentDependencies = {}", compDependencyMap)
      log.debug("components = {}", components.mkString("\n", "\n\n", "\n"))
    }

    val app = compDependencyMap match {
      case Some(map) => shutdownComponentsWithoutDependents(this, components, map)
      case None => shutdownComponentsWithoutDependents(this, components, Map[String, List[String]]())
    }

    publish(AfterApplicationShutdown(this))
    app
  }

}

class ComponentNotFoundException(componentName: String) extends IllegalArgumentException(componentName)

