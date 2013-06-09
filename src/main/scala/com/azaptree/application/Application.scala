package com.azaptree.application

import scala.annotation.tailrec
import org.slf4j.LoggerFactory

case class Application(components: List[Component[ComponentStarted, _]] = Nil) {

  def register(comp: Component[ComponentNotConstructed, _]): Application = {
    assert(components.find(_.name == comp.name).isEmpty, "A component with the same name is already registered: " + comp.name)

    val compStarted = comp.startup()
    copy(components = compStarted :: components)
  }

  def shutdownComponent(componentName: String): Either[Exception, Application] = {
    val (componentToShutdown, remaining) = components.span(_.name == componentName)
    if (componentToShutdown.isEmpty) {
      Left(new ComponentNotFoundException(componentName))
    } else {
      componentToShutdown.foreach(_.shutdown)
      Right(copy(components = remaining))
    }
  }

  def shutdown() = {
    val log = LoggerFactory.getLogger(getClass())

    @tailrec
    def shutdownComponentsWithoutDependents(components: Iterable[Component[ComponentStarted, _]], componentDependencies: Map[String, List[String]]): Unit = {
      if (!components.isEmpty) {
        val dependencySet = componentDependencies.values.foldLeft(Set[String]())((set, compName) => set ++ compName)
        log.debug("dependencySet = {}", dependencySet)

        val compsWithoutDependents = components.filter(c => !dependencySet.contains(c.name))

        if (log.isDebugEnabled()) {
          log.debug("compsWithoutDependents = {}", compsWithoutDependents.mkString("\n", "\n\n", "\n"))
        }

        var componentDependenciesTemp = componentDependencies
        compsWithoutDependents.foreach(c => {
          c.shutdown()
          componentDependenciesTemp -= c.name
        })

        val compsWithDependents = components.filterNot(c => !dependencySet.contains(c.name))
        shutdownComponentsWithoutDependents(compsWithDependents, componentDependenciesTemp)
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

    compDependencyMap match {
      case Some(map) => shutdownComponentsWithoutDependents(components, map)
      case None => shutdownComponentsWithoutDependents(components, Map[String, List[String]]())
    }

  }

}

class ComponentNotFoundException(componentName: String) extends IllegalArgumentException(componentName)

