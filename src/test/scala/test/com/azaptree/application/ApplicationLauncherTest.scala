

package test.com.azaptree.application

import scala.concurrent.ExecutionContext.Implicits.global

import com.azaptree.application.ApplicationLauncher
import com.azaptree.application.ApplicationService
import com.azaptree.application.Component
import com.azaptree.application.ComponentConstructed
import com.azaptree.application.ComponentInitialized
import com.azaptree.application.ComponentLifeCycle
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.application.ComponentStarted
import com.azaptree.application.ComponentStopped
import com.azaptree.application.healthcheck._
import com.azaptree.application.healthcheck.HealthCheck
import com.azaptree.application.healthcheck.HealthCheckConfig
import com.azaptree.application.healthcheck.HealthCheckInfo
import com.typesafe.config.ConfigFactory

import TestApplicationLauncher._

object TestApplicationLauncher {
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

  sealed trait Event

  sealed trait Event2 extends Event

  case object EventA extends Event

  case object EventB extends Event

  case object EventC extends Event2
}

class TestApplicationLauncher extends ApplicationLauncher {

  val compAHealthCheck = HealthCheck(
    info = HealthCheckInfo(group = "GROUP-1", name = "compAHealthCheck")(description = "CompA HealthCheck"),
    config = HealthCheckConfig(config = Some(ConfigFactory.parseString("""compName = "CompA" """))))
  val compCHealthCheck = HealthCheck(
    info = HealthCheckInfo(group = "GROUP-10", name = "compCHealthCheck")(description = "CompC HealthCheck"),
    config = HealthCheckConfig(config = Some(ConfigFactory.parseString("""compName = "CompC" """))))
  val compEHealthCheck = HealthCheck(
    info = HealthCheckInfo(name = "compEHealthCheck")(description = "CompE HealthCheck"),
    config = HealthCheckConfig(config = Some(ConfigFactory.parseString("""compName = "CompE" """))))

  val greenCheckScorer: HealthCheckScorer = (healthCheck, appService) => (100, None)
  val yellowCheckScorer: HealthCheckScorer = (healthCheck, appService) => (80, None)
  val redCheckScorer: HealthCheckScorer = (healthCheck, appService) => (50, Some("ALERT!!!"))

  import scala.concurrent.ExecutionContext.Implicits.global

  val compA = Component[ComponentNotConstructed, Comp](name = "CompA", componentLifeCycle = new CompLifeCycle(),
    healthChecks = Some((compAHealthCheck, healthCheckRunner(greenCheckScorer)) :: Nil))
  val compB = Component[ComponentNotConstructed, Comp](name = "CompB", componentLifeCycle = new CompLifeCycle())
  val compC = Component[ComponentNotConstructed, Comp](name = "CompC", componentLifeCycle = new CompLifeCycle(),
    healthChecks = Some((compCHealthCheck, healthCheckRunner(yellowCheckScorer)) :: Nil))
  val compD = Component[ComponentNotConstructed, Comp](name = "CompD", componentLifeCycle = new CompLifeCycle())
  val compE = Component[ComponentNotConstructed, Comp](name = "CompE", componentLifeCycle = new CompLifeCycle(),
    healthChecks = Some((compEHealthCheck, healthCheckRunner(redCheckScorer)) :: Nil))

  var comps = Vector[Component[ComponentNotConstructed, Comp]]()
  comps = comps :+ compA.copy[ComponentNotConstructed, Comp](dependsOn = Some((compB :: compD :: Nil)))
  comps = comps :+ compB.copy[ComponentNotConstructed, Comp](dependsOn = Some((compC :: Nil)))
  comps = comps :+ compC
  comps = comps :+ compD.copy[ComponentNotConstructed, Comp](dependsOn = Some((compB :: Nil)))
  comps = comps :+ compE.copy[ComponentNotConstructed, Comp](dependsOn = Some((compD :: Nil)))

  val appHealthCheck1 = HealthCheck(
    info = HealthCheckInfo(group = "GROUP-1", name = "appHealthCheck1")(description = "appHealthCheck1"),
    config = HealthCheckConfig(config = Some(ConfigFactory.parseString("""compName = "CompA" """))))

  val appHealthCheck2 = HealthCheck(
    info = HealthCheckInfo(group = "GROUP-1", name = "appHealthCheck2")(description = "appHealthCheck2"),
    config = HealthCheckConfig(config = Some(ConfigFactory.parseString("""compName = "CompB" """))))

  val appHealthCheck3 = HealthCheck(
    info = HealthCheckInfo(group = "GROUP-2", name = "appHealthCheck3")(description = "appHealthCheck3"),
    config = HealthCheckConfig(config = Some(ConfigFactory.parseString("""compName = "CompC" """))))

  val checkCompStartedScorer: HealthCheckScorer = (healthCheck, appService) => {
    val compName = healthCheck.config.config.get.getString("compName")
    if (appService.isComponentStarted(compName)) (100, None) else (0, Some(s"$compName is not started"))
  }

  override def createApplicationService(): ApplicationService = {
    val app = new ApplicationService()
    comps.foreach(app.registerComponent(_))
    app.addHealthCheck(appHealthCheck1, healthCheckRunner(checkCompStartedScorer))
    app.addHealthCheck(appHealthCheck2, healthCheckRunner(checkCompStartedScorer))
    app.addHealthCheck(appHealthCheck3, healthCheckRunner(checkCompStartedScorer))

    app
  }
}

object ApplicationLauncherTest extends App {
  val launcher = new TestApplicationLauncher()
  launcher.launch()
}