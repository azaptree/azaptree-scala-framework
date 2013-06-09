package com.azaptree.application

//
//import types._
//
//case class Application(components: Iterable[Tuple2[AnyComponent, AnyStartedComponentInstance]], componentDependencies: Option[Map[String, List[String]]] = None) {
//
//  def shutdown() = {
//    @tailrec
//    def shutdownComponentsWithoutDependents(components: Iterable[Tuple2[AnyComponent, AnyStartedComponentInstance]],
//      componentDependencies: Map[String, List[String]]): Unit = {
//      if (!components.isEmpty) {
//        val (compsWithDependents, compsWithoutDependents) = components.span(c => componentDependencies.find(m => m._2.contains(c._1.name)).isDefined)
//
//        var componentDependenciesTemp = componentDependencies
//        compsWithoutDependents.foreach(c => {
//          c._1.shutdown(c._2)
//          componentDependenciesTemp -= c._1.name
//        })
//        shutdownComponentsWithoutDependents(compsWithDependents, componentDependenciesTemp)
//      }
//    }
//
//    componentDependencies match {
//      case Some(map) => shutdownComponentsWithoutDependents(components, map)
//      case None => shutdownComponentsWithoutDependents(components, Map[String, List[String]]())
//    }
//
//  }
//
//}
//
//class ApplicationBuilder {
//
//  var components = Vector[Tuple2[AnyComponent, AnyStartedComponentInstance]]()
//  var componentDependencies = Map[String, List[String]]()
//
//  def +=(comp: AnyComponent, compInstance: AnyStartedComponentInstance, dependsOn: List[AnyComponent]): this.type = {
//    assert(!dependsOn.isEmpty, "if specified, then cannot be empty")
//    +=(comp, compInstance)
//    componentDependencies += new Tuple2(comp.name, dependsOn.map(_.name))
//    this
//  }
//
//  def +=(comp: AnyComponent, compInstance: AnyStartedComponentInstance): this.type = {
//    assert(components.find(_._1.name == comp.name).isEmpty, s"Component with the same name is already registered : $comp.name")
//    components = components :+ (comp, compInstance)
//    this
//  }
//
//  def result: Application = {
//    if (componentDependencies.isEmpty) Application(components, None) else Application(components, Some(componentDependencies))
//  }
//
//}

