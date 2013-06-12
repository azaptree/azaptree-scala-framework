package test.com.azaptree.application

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.application.Component
import com.azaptree.application.ComponentConstructed
import com.azaptree.application.ComponentInitialized
import com.azaptree.application.ComponentLifeCycle
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.application.ComponentStarted
import com.azaptree.application.ComponentStopped
import ApplicationServiceSpec._
import com.azaptree.application.ApplicationService

object ApplicationServiceSpec {
  val started = "ComponentStarted"
  val initialized = "ComponentInitialized"
  val constructed = "ComponentConstructed"

  var reverseShutdownOrder: List[String] = Nil

  case class Comp(state: List[String] = Nil) {
    def addState(newState: String): Comp = {
      copy(state = newState :: state)
    }
  }

  class CompLifeCycle extends ComponentLifeCycle[Comp] {
    protected def create(comp: Component[ComponentNotConstructed, Comp]): Component[ComponentConstructed, Comp] = {
      comp.copy[ComponentConstructed, Comp](componentLifeCycle = this, componentObject = Some(Comp(constructed :: Nil)))
    }

    override protected def init(comp: Component[ComponentConstructed, Comp]): Component[ComponentInitialized, Comp] = {
      val Comp = comp.componentObject.get
      comp.copy[ComponentInitialized, Comp](componentObject = Some(Comp.addState(initialized)))
    }

    override protected def start(comp: Component[ComponentInitialized, Comp]): Component[ComponentStarted, Comp] = {
      val Comp = comp.componentObject.get
      val compStarted = comp.copy[ComponentStarted, Comp](componentObject = Some(Comp.addState(started)))
      compStarted
    }

    override protected def stop(comp: Component[ComponentStarted, Comp]): Component[ComponentStopped, Comp] = {
      val compStopped = comp.copy[ComponentStopped, Comp](componentObject = None)
      reverseShutdownOrder = comp.name :: reverseShutdownOrder
      compStopped
    }
  }

  class CompLifeCycleWithShutdownFailure extends ComponentLifeCycle[Comp] {
    protected def create(comp: Component[ComponentNotConstructed, Comp]): Component[ComponentConstructed, Comp] = {
      comp.copy[ComponentConstructed, Comp](componentLifeCycle = this, componentObject = Some(Comp(constructed :: Nil)))
    }

    override protected def init(comp: Component[ComponentConstructed, Comp]): Component[ComponentInitialized, Comp] = {
      val Comp = comp.componentObject.get
      comp.copy[ComponentInitialized, Comp](componentObject = Some(Comp.addState(initialized)))
    }

    override protected def start(comp: Component[ComponentInitialized, Comp]): Component[ComponentStarted, Comp] = {
      val Comp = comp.componentObject.get
      val compStarted = comp.copy[ComponentStarted, Comp](componentObject = Some(Comp.addState(started)))
      compStarted
    }

    override protected def stop(comp: Component[ComponentStarted, Comp]): Component[ComponentStopped, Comp] = {
      throw new RuntimeException("SHUTDOWN ERROR")
    }
  }
}

class ApplicationServiceSpec extends FunSpec with ShouldMatchers {

  describe("An ApplicationService") {
    it("is used to start and stop components defined for an Application") {
      val compA = Component[ComponentNotConstructed, Comp]("CompA", new CompLifeCycle())
      val compB = Component[ComponentNotConstructed, Comp]("CompB", new CompLifeCycle())
      val compC = Component[ComponentNotConstructed, Comp]("CompC", new CompLifeCycle())
      val compD = Component[ComponentNotConstructed, Comp]("CompD", new CompLifeCycle())
      val compE = Component[ComponentNotConstructed, Comp]("CompE", new CompLifeCycle())

      var comps = Vector[Component[ComponentNotConstructed, Comp]]()
      comps = comps :+ compA.copy[ComponentNotConstructed, Comp](dependsOn = Some((compB :: compD :: Nil)))
      comps = comps :+ compB.copy[ComponentNotConstructed, Comp](dependsOn = Some((compC :: Nil)))
      comps = comps :+ compC
      comps = comps :+ compD.copy[ComponentNotConstructed, Comp](dependsOn = Some((compB :: Nil)))
      comps = comps :+ compE.copy[ComponentNotConstructed, Comp](dependsOn = Some((compD :: Nil)))

      println(comps.mkString("\n\n***************** comps *****************\n", "\n\n", "\n*************************************\n"))

      val compCreator: ApplicationService.ComponentCreator = () => comps.toList
      val app = new ApplicationService(compCreator)

      app.start()

      println("*** app components = " + app.componentNames.mkString("\n\n", "\n", "\n\n"))

      app.stop()

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

    it("can return the names of components that have been registered with the ApplicationService") {
      pending
    }

    it("is used used to register components on an individual basis") {
      pending
    }

    it("will start up all components that have been supplied at ApplicationService construction time") {
      pending
    }

    it("can publish ApplicationEvents") {
      pending
    }

    it("implements a subchannel event bus") {
      pending
    }

    it("can start and stop registered components on an individual basis") {
      pending
    }

    it("can return the names of all components that have been started") {
      pending
    }

    it("can tell you if a component has been started") {
      pending
    }

    it("can be used to retrieve a component object for started component") {
      pending
    }

    it("can return the component object class for a started component") {
      pending
    }

    it("can tell you if the application is running, which is defined as at least one component has started") {
      pending
    }

    it("""can run all component health checks.""") {
      pending
    }

    it("can run all application health checks") {
      pending
    }

    it("can run individual component health checks") {
      pending
    }

    it("can run individual application health checks") {
      pending
    }

    it("can run health checks for a specific group") {
      pending
    }

    it("runs Healthchecks asynchrously - health check results are returned wrapped in a Future") {
      pending
    }

  }

}