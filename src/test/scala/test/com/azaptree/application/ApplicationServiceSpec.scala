package test.com.azaptree.application

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
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
import ApplicationServiceSpec._
import ApplicationServiceSpec._
import scala.concurrent.Future
import scala.concurrent.Await
import com.azaptree.application.InvalidComponentNameException

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

  sealed trait Event

  sealed trait Event2 extends Event

  case object EventA extends Event

  case object EventB extends Event

  case object EventC extends Event2
}

class ApplicationServiceSpec extends FunSpec with ShouldMatchers {

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

  def createApp() = {
    println(comps.mkString("\n\n***************** comps *****************\n", "\n\n", "\n*************************************\n"))

    val compCreator = comps.toList
    val app = new ApplicationService()

    comps.foreach(app.registerComponent(_))

    app.addHealthCheck(appHealthCheck1, healthCheckRunner(checkCompStartedScorer))
    app.addHealthCheck(appHealthCheck2, healthCheckRunner(checkCompStartedScorer))
    app.addHealthCheck(appHealthCheck3, healthCheckRunner(checkCompStartedScorer))

    app
  }

  describe("An ApplicationService") {
    it("is used to start and stop components defined for an Application") {
      val app = createApp()

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
      val app = createApp()
      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)

      app.componentNames.size should be(comps.size)
      comps.foreach(c => assert(app.componentNames.find(name => name == c.name).isDefined, {
        val compName = c.name
        val compNames = app.componentNames
        s"app does does not contain [$compName] within : $compNames"
      }))

    }

    it("is used used to register components on an individual basis") {
      val app = createApp()

      val compF = Component[ComponentNotConstructed, Comp]("CompF", new CompLifeCycle())
      app.registerComponent(compF)
      app.componentNames should contain(compF.name)
      app.componentNames.size should be(comps.size + 1)
    }

    it("will start up all components that have been supplied at ApplicationService construction time") {
      val app = createApp()

      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)

      app.start()

      app.isRunning() should be(true)
      app.startedComponentNames.isEmpty should be(false)
      app.startedComponentNames.size should be(app.componentNames.size)

      app.stop()
      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)
    }

    it("can publish ApplicationEvents") {
      val app = createApp()

      var count = 0;
      app.subscribe((event) => {
        count += 1
      }, classOf[String])

      app.publish("TEST MESSAGE");
      Thread.sleep(10l)
      count should be(1)
    }

    it("implements a subchannel event bus") {
      val app = createApp()

      var eventCount = 0;
      app.subscribe((event) => {
        eventCount += 1
      }, classOf[Event])

      var event2Count = 0;
      app.subscribe((event) => {
        event2Count += 1
      }, classOf[Event2])

      app.publish(EventA);
      app.publish(EventB);
      app.publish(EventC);
      Thread.sleep(10l)
      eventCount should be(3)
      event2Count should be(1)
    }

    it("can start and stop registered components on an individual basis") {
      val app = createApp()

      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)

      app.start()

      app.isRunning() should be(true)
      app.startedComponentNames.isEmpty should be(false)
      app.startedComponentNames.size should be(app.componentNames.size)

      app.startedComponentNames.foreach(app.stopComponent(_))

      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)

      app.componentNames.foreach(app.startComponent(_))

      app.isRunning() should be(true)
      app.startedComponentNames.isEmpty should be(false)
      app.startedComponentNames.size should be(app.componentNames.size)

      app.stop()
      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)
    }

    it("can return the names of all components that have been started") {
      val app = createApp()

      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)

      app.start()

      app.isRunning() should be(true)
      app.startedComponentNames.isEmpty should be(false)
      app.startedComponentNames.size should be(app.componentNames.size)

      app.stopComponent(app.startedComponentNames.head)

      app.startedComponentNames.size should be(app.componentNames.size - 1)
    }

    it("can tell you if a component has been started") {
      val app = createApp()

      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)

      app.start()

      app.isRunning() should be(true)
      app.startedComponentNames.isEmpty should be(false)
      app.startedComponentNames.size should be(app.componentNames.size)

      app.stopComponent(app.startedComponentNames.head)

      app.startedComponentNames.size should be(app.componentNames.size - 1)

      app.startedComponentNames.foreach(name => assert(app.isComponentStarted(name)))
    }

    it("can be used to retrieve a component object for a started component") {
      val app = createApp()

      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)

      app.start()

      app.isRunning() should be(true)
      app.startedComponentNames.isEmpty should be(false)
      app.startedComponentNames.size should be(app.componentNames.size)

      val compObjects = app.startedComponentNames.map(app.getStartedComponentObject(_))
      compObjects.foreach { c =>
        assert(c.isDefined)
        println("- can be used to retrieve a component object for a started component : " + c)
      }

      app.getStartedComponentObject[Comp](compA.name).isDefined should be(true)
      println("- can be used to retrieve a component object for a started component : " + app.getStartedComponentObject[Comp](compA.name))

      app.stopComponent(compA.name)
      app.getStartedComponentObject[Comp](compA.name).isDefined should be(false)

    }

    it("can return the component object class for a started component") {
      val app = createApp()

      app.isRunning() should be(false)
      app.startedComponentNames.isEmpty should be(true)

      app.start()

      app.isRunning() should be(true)
      app.startedComponentNames.isEmpty should be(false)
      app.startedComponentNames.size should be(app.componentNames.size)

      val compObjects = app.startedComponentNames.map(app.getStartedComponentObjectClass(_))
      compObjects.foreach(c => assert(c.isDefined))

      app.getStartedComponentObjectClass(compA.name).isDefined should be(true)
      app.getStartedComponentObjectClass(compA.name).get //should be(classOf[Comp])

      app.stopComponent(compA.name)
      app.getStartedComponentObjectClass(compA.name).isDefined should be(false)
    }

    it("can return a component's dependencies") {
      val app = createApp()

      for {
        comp <- comps
      } yield {
        app.componentDependencies(comp.name) match {
          case Left(l) => throw l
          case Right(dependencies) =>
            val dependencyCount = dependencies.getOrElse(Nil).size
            val expectedNames = comp.dependsOn.getOrElse(Nil).map(_.name).toList
            assert(dependencyCount == expectedNames.size, "Expected does not match actual : %s != %s".format(expectedNames.size, dependencyCount))
            dependencies.foreach { list =>
              list.foreach(name => assert(expectedNames.contains(name)))
            }
        }
      }

    }

    it("can return a component's dependendents") {
      val app = createApp()

      for {
        comp <- comps
      } yield {
        app.componentDependents(comp.name) match {
          case Left(l) => throw l
          case Right(dependenciesOption) =>
            dependenciesOption.foreach { dependencies =>
              comp.name match {
                case compA.name => assert(dependencies.isEmpty, "compA should have no dependents")
                case compB.name =>
                  val expectedNames = compA.name :: compD.name :: compE.name :: Nil
                  assert(dependencies.size == expectedNames.size, "Expected does not match actual : %s != %s".format(expectedNames.size, dependencies.size))
                  dependencies.foreach(name => assert(expectedNames.contains(name)))
                case compC.name =>
                case compD.name =>
                case compE.name =>
                case _ =>
              }
            }
        }

      }

    }

    it("can tell you if the application is running, which is defined as at least one component has started") {
      val app = createApp()

      for (i <- 1 to 10) {
        app.isRunning should be(false)
        app.start()
        app.isRunning should be(true)
        app.stop()
        app.isRunning should be(false)
      }

    }

    it("""can run all component health checks.""") {
      val app = createApp()
      app.start()

      app.runComponentHealthChecks() match {
        case Some(healthCheckResultsFutures) =>
          assert(healthCheckResultsFutures.size == 3)

          import scala.concurrent.duration._

          for {
            healthCheckResult <- healthCheckResultsFutures.map(Await.result(_, 1 second))
          } yield {
            println(s"healthCheckResult = $healthCheckResult")
            healthCheckResult match {
              case h if (h.healthCheck.info.name == compA.name) =>
                assert(h.healthCheckIndicator == GREEN)
              case h if (h.healthCheck.info.name == compC.name) =>
                assert(h.healthCheckIndicator == YELLOW)
              case h if (h.healthCheck.info.name == compE.name) =>
                assert(h.healthCheckIndicator == RED)
                assert(h.info.isDefined)
              case _ =>
            }
          }

        case None => throw new IllegalStateException("Expected HealthCheckResults to be returned")
      }

      app.stop()
      assert(app.runComponentHealthChecks().isEmpty)

    }

    it("can run all application health checks") {
      val app = createApp()
      app.start()

      app.runApplicationHealthChecks match {
        case Some(futures) =>
          futures.foreach { f =>
            import scala.concurrent.duration._
            val healthCheckResult = Await.result(f, 1 second)
            if (healthCheckResult.healthCheck.info.name == appHealthCheck1.info.name) {
              assert(healthCheckResult.healthCheckIndicator == GREEN)
            }
          }

        case None => throw new IllegalStateException("Application health checks should be registered")
      }

      app.stopComponent(compA.name)
      app.runApplicationHealthChecks match {
        case Some(futures) =>
          futures.foreach { f =>
            import scala.concurrent.duration._
            val healthCheckResult = Await.result(f, 1 second)
            if (healthCheckResult.healthCheck.info.name == appHealthCheck1.info.name) {
              assert(healthCheckResult.healthCheckIndicator == RED)
              assert(healthCheckResult.info.isDefined)
            }
          }

        case None => throw new IllegalStateException("Application health checks should be registered")
      }
    }

    it("can run individual component health checks") {
      val app = createApp()
      app.start()

      app.runComponentHealthChecks(compA.name) match {
        case Left(e) => throw e
        case Right(healthCheckResults) =>
          healthCheckResults match {
            case None => throw new IllegalStateException("Expected health check results for component : " + compA.name)
            case Some(futures) =>
              import scala.concurrent.duration._
              val healthCheckResult = Await.result(futures.head, 1 second)
              assert(healthCheckResult.healthCheckIndicator == GREEN)
          }
      }

      app.stopComponent(compA.name)

      app.runComponentHealthChecks(compA.name) match {
        case Left(e: InvalidComponentNameException) => //expected
        case Right(healthCheckResults) =>
          assert(healthCheckResults.isEmpty, "Since component is stopped, did not expect health checks to be run for component : " + compA.name)
      }

      app.startComponent(compA.name)

      app.runComponentHealthChecks(compA.name) match {
        case Left(e) => throw e
        case Right(healthCheckResults) =>
          assert(healthCheckResults.isDefined, "Expected HealthCheckResults since the component should be started")
      }
    }

    it("can run individual application health checks") {
      val app = createApp()
      app.start()

      info("Running health check that will check that CompA is started")
      val healtcheckName = appHealthCheck1.info.name

      app.runApplicationHealthCheck(healtcheckName) match {
        case Some(f) =>
          import scala.concurrent.duration._
          val healthCheckResult = Await.result(f, 1 second)
          assert(healthCheckResult.healthCheck.info.name == appHealthCheck1.info.name)
          assert(healthCheckResult.healthCheckIndicator == GREEN)

        case None => throw new IllegalStateException("Application health checks should be registered")
      }

      info("Stopping component to check that the healthcheck fails.")
      app.stopComponent(compA.name)
      app.runApplicationHealthCheck(healtcheckName) match {
        case Some(f) =>
          import scala.concurrent.duration._
          val healthCheckResult = Await.result(f, 1 second)
          assert(healthCheckResult.healthCheck.info.name == appHealthCheck1.info.name)
          assert(healthCheckResult.healthCheckIndicator == RED)
          assert(healthCheckResult.info.isDefined)
        case None => throw new IllegalStateException("Application health checks should be registered")
      }
    }

    it("can run health checks for a specific group") {
      val app = createApp()
      app.start()

      app.runAllHealthChecksByGroup(appHealthCheck1.info.group) match {
        case None => throw new IllegalStateException("Expected health checks for group: " + appHealthCheck1.info.group)
        case Some(futures) =>
          futures.size should be(3)
      }

      app.stop

      app.runAllHealthChecksByGroup(appHealthCheck1.info.group) match {
        case None => throw new IllegalStateException("Expected health checks for group: " + appHealthCheck1.info.group)
        case Some(futures) =>
          assert(futures.size == 2)
      }
    }

    it("can return all registered application health checks") {
      val app = createApp()
      val healthChecksBeforeStartingApp = app.applicationHealthChecks.get
      healthChecksBeforeStartingApp.size should be(3)

      app.start()
      healthChecksBeforeStartingApp.size should be(app.applicationHealthChecks.get.size)
    }

    it("can return health checks for a specific component") {
      val app = createApp()
      app.start

      app.componentHealthChecks(compA.name).isDefined should be(true)
      app.componentHealthChecks(compB.name).isDefined should be(false)

      app.stop
      app.componentHealthChecks(compA.name).isDefined should be(false)
      app.componentHealthChecks(compB.name).isDefined should be(false)
    }

  }

}