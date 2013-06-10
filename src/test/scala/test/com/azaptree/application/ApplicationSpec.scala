package test.com.azaptree.application

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSpec
import com.azaptree.application.ComponentInitialized
import com.azaptree.application.ComponentStopped
import com.azaptree.application.ComponentLifeCycle
import com.azaptree.application.ComponentStarted
import com.azaptree.application.Component
import com.azaptree.application.ComponentConstructed
import com.azaptree.application.ComponentNotConstructed

import ApplicationSpec._
import com.azaptree.application.Application

object ApplicationSpec {
  val started = "ComponentStarted"
  val initialized = "ComponentInitialized"
  val constructed = "ComponentConstructed"

  var reverseShutdownOrder: List[String] = Nil

  case class CompA(state: List[String] = Nil) {
    def addState(newState: String): CompA = {
      copy(state = newState :: state)
    }
  }

  class CompALifeCycle extends ComponentLifeCycle[CompA] {
    protected def create(comp: Component[ComponentNotConstructed, CompA]): Component[ComponentConstructed, CompA] = {
      comp.copy[ComponentConstructed, CompA](componentLifeCycle = this, componentObject = Some(CompA(constructed :: Nil)))
    }

    override protected def init(comp: Component[ComponentConstructed, CompA]): Component[ComponentInitialized, CompA] = {
      val compA = comp.componentObject.get
      comp.copy[ComponentInitialized, CompA](componentObject = Some(compA.addState(initialized)))
    }

    override protected def start(comp: Component[ComponentInitialized, CompA]): Component[ComponentStarted, CompA] = {
      val compA = comp.componentObject.get
      val compStarted = comp.copy[ComponentStarted, CompA](componentObject = Some(compA.addState(started)))
      println("Started Component : " + comp.name)
      compStarted
    }

    override protected def stop(comp: Component[ComponentStarted, CompA]): Component[ComponentStopped, CompA] = {
      val compStopped = comp.copy[ComponentStopped, CompA](componentObject = None)
      reverseShutdownOrder = comp.name :: reverseShutdownOrder
      println("Stopped Component : " + comp.name)
      compStopped
    }
  }
}

class ApplicationSpec extends FunSpec with ShouldMatchers {

  describe("An Application will shutdown Components in the proper order") {
    it("can shutdown itself using it's registered ComponentLifeCycle") {
      val compA = Component[ComponentNotConstructed, CompA]("CompA", new CompALifeCycle())
      val compB = Component[ComponentNotConstructed, CompA]("CompB", new CompALifeCycle())
      val compC = Component[ComponentNotConstructed, CompA]("CompC", new CompALifeCycle())
      val compD = Component[ComponentNotConstructed, CompA]("CompD", new CompALifeCycle())
      val compE = Component[ComponentNotConstructed, CompA]("CompE", new CompALifeCycle())

      var comps = Vector[Component[ComponentNotConstructed, CompA]]()
      comps = comps :+ compA.copy[ComponentNotConstructed, CompA](dependsOn = Some((compB :: compD :: Nil)))
      comps = comps :+ compB.copy[ComponentNotConstructed, CompA](dependsOn = Some((compC :: Nil)))
      comps = comps :+ compC
      comps = comps :+ compD.copy[ComponentNotConstructed, CompA](dependsOn = Some((compB :: Nil)))
      comps = comps :+ compE.copy[ComponentNotConstructed, CompA](dependsOn = Some((compD :: Nil)))

      println(comps.mkString("\n\n***************** comps *****************\n", "\n\n", "\n*************************************\n"))

      val app = comps.foldLeft(Application()) { (app, comp) =>
        println("\n" + app + "\n")
        app.register(comp)
      }

      println("*** app components = " + app.components.mkString("\n\n", "\n", "\n\n"))

      val appShutdowned = app.shutdown()

      val shutdownOrder = reverseShutdownOrder.reverse
      println("*** shutdownOrder = " + shutdownOrder)

      shutdownOrder.indexOf("CompA") should be < 2
      shutdownOrder.indexOf("CompE") should be < 2
      shutdownOrder.indexOf("CompD") should be(2)
      shutdownOrder.indexOf("CompB") should be(3)
      shutdownOrder.indexOf("CompC") should be(4)

      shutdownOrder match {
        case ("CompE" :: "CompA" :: "CompD" :: "CompB" :: "CompC" :: Nil) =>
        case ("CompA" :: "CompE" :: "CompD" :: "CompB" :: "CompC" :: Nil) =>
        case _ => fail("Shutdown order is not correct")
      }
    }

    it("will publish events when a component is registered") {
      pending
    }

    it("will publish events when a component is started") {
      pending
    }

    it("will publish events when a component is stopped") {
      pending
    }

    it("will publish events before and after the application is shutdown") {
      pending
    }
  }
}