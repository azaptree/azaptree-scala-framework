package test.com.azaptree.application

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import ComponentSpec._
import com.azaptree.application.ComponentLifeCycle
import com.azaptree.application.ComponentStopped
import com.azaptree.application.ComponentStarted
import com.azaptree.application.ComponentInitialized
import com.azaptree.application.Component
import com.azaptree.application.ComponentConstructed
import com.azaptree.application.ComponentNotConstructed

object ComponentSpec {
  val started = "ComponentStarted"
  val initialized = "ComponentInitialized"
  val constructed = "ComponentConstructed"

  case class CompA(state: List[String] = Nil) {
    def addState(newState: String): CompA = {
      copy(state = newState :: state)
    }
  }

  class CompALifeCycle extends ComponentLifeCycle[CompA] {
    protected def create(comp: Component[ComponentNotConstructed, CompA]): Component[ComponentConstructed, CompA] = {
      Component[ComponentConstructed, CompA](name = "CompA", componentLifeCycle = this, componentObject = Some(CompA(constructed :: Nil)))
    }

    override protected def init(comp: Component[ComponentConstructed, CompA]): Component[ComponentInitialized, CompA] = {
      val compA = comp.componentObject.get
      comp.copy[ComponentInitialized, CompA](componentObject = Some(compA.addState(initialized)))
    }

    override protected def start(comp: Component[ComponentInitialized, CompA]): Component[ComponentStarted, CompA] = {
      val compA = comp.componentObject.get
      comp.copy[ComponentStarted, CompA](componentObject = Some(compA.addState(started)))
    }

    override protected def stop(comp: Component[ComponentStarted, CompA]): Component[ComponentStopped, CompA] = {
      comp.copy[ComponentStopped, CompA](componentObject = None)
    }
  }

}

class ComponentSpec extends FunSpec with ShouldMatchers {

  describe("A Component") {
    it("can shutdown itself using it's registered ComponentLifeCycle") {
      val compA = Component[ComponentNotConstructed, CompA]("CompA", new CompALifeCycle())
      val compAStarted = compA.startup()
      compAStarted.componentObject.get.state should have size (3)
      compAStarted.componentObject.get.state should be((started :: initialized :: constructed :: Nil))
    }
  }

}