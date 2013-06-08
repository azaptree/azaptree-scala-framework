package com.azaptree.application

import scala.reflect.ClassTag
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.azaptree.application.lifecycle._
import scala.annotation.tailrec

case class Application(components: Iterable[Tuple2[Component[ComponentInstance], ComponentInstance[Started]]], componentDependencies: Option[Map[String, List[String]]] = None) {

  def shutdown() = {
    @tailrec
    def shutdownComponentsWithoutDependents(components: Iterable[Tuple2[Component[ComponentInstance], ComponentInstance[Started]]],
      componentDependencies: Map[String, List[String]]): Unit = {
      if (!components.isEmpty) {
        val (compsWithDependents, compsWithoutDependents) = components.span(c => componentDependencies.find(m => m._2.contains(c._1.name)).isDefined)

        var componentDependenciesTemp = componentDependencies
        compsWithoutDependents.foreach(c => {
          c._1.shutdown(c._2)
          componentDependenciesTemp -= c._1.name
        })
        shutdownComponentsWithoutDependents(compsWithDependents, componentDependenciesTemp)
      }
    }

    componentDependencies match {
      case Some(map) => shutdownComponentsWithoutDependents(components, map)
      case None => shutdownComponentsWithoutDependents(components, Map[String, List[String]]())
    }

  }

}

class ApplicationBuilder {

  var components = Vector[Tuple2[Component[ComponentInstance], ComponentInstance[Started]]]()
  var componentDependencies = Map[String, List[String]]()

  def +=(comp: Component[ComponentInstance], compInstance: ComponentInstance[Started], dependsOn: Option[List[String]] = None): this.type = {
    assert(components.find(_._1.name == comp.name).isEmpty, s"Component with the same name is already registered : $comp.name")

    components = components :+ (comp, compInstance)

    dependsOn.foreach(compDependencyNames =>
      componentDependencies += new Tuple2(comp.name, compDependencyNames))

    this
  }

  def result: Application = {
    if (componentDependencies.isEmpty) Application(components, None) else Application(components, Some(componentDependencies))
  }

}

