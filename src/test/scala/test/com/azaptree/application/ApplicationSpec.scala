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
import com.azaptree.application.ComponentStartedEvent
import com.azaptree.application.ComponentShutdownEvent
import com.azaptree.application.PreApplicationShutdownEvent
import com.azaptree.application.PostApplicationShutdownEvent
import com.azaptree.application.ComponentShutdownFailedEvent
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.LoggerFactory

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
      compStarted
    }

    override protected def stop(comp: Component[ComponentStarted, CompA]): Component[ComponentStopped, CompA] = {
      val compStopped = comp.copy[ComponentStopped, CompA](componentObject = None)
      reverseShutdownOrder = comp.name :: reverseShutdownOrder
      compStopped
    }
  }

  class CompALifeCycleWithShutdownFailure extends ComponentLifeCycle[CompA] {
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
      compStarted
    }

    override protected def stop(comp: Component[ComponentStarted, CompA]): Component[ComponentStopped, CompA] = {
      throw new RuntimeException("SHUTDOWN ERROR")
    }
  }
}

class ApplicationSpec extends FunSpec with ShouldMatchers {
  val log = LoggerFactory.getLogger(("ApplicationSpec"))

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

      log.info(comps.mkString("\n\n***************** comps *****************\n", "\n\n", "\n*************************************\n"))

      val app = comps.foldLeft(Application()) { (app, comp) =>
        log.info("\n" + app + "\n")
        app.register(comp)._1
      }

      log.info("*** app components = " + app.components.mkString("\n\n", "\n", "\n\n"))

      val appShutdowned = app.shutdown()

      val shutdownOrder = reverseShutdownOrder.reverse
      log.info("*** shutdownOrder = " + shutdownOrder)

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
      val compA = Component[ComponentNotConstructed, CompA]("CompA", new CompALifeCycle())
      val compB = Component[ComponentNotConstructed, CompA]("CompB", new CompALifeCycle())
      val compC = Component[ComponentNotConstructed, CompA]("CompC", new CompALifeCycle())

      var compRegisteredCount = new AtomicInteger(0)
      val subscriber: Any => Unit = event => {
        compRegisteredCount.incrementAndGet()
        log.info(compRegisteredCount + " : " + event)
      }

      var app = Application()
      app.eventBus.subscribe(subscriber, classOf[ComponentStartedEvent]);
      val comps = (compA :: compB :: compC :: Nil)
      app = comps.foldLeft(app) { (app, comp) =>
        log.info("\n" + app + "\n")
        app.register(comp)._1
      }

      Thread.sleep(100l)
      compRegisteredCount.get() should be(comps.size)

    }

    it("will publish events when a component is stopped") {
      val compA = Component[ComponentNotConstructed, CompA]("CompA", new CompALifeCycle())
      val compB = Component[ComponentNotConstructed, CompA]("CompB", new CompALifeCycle())
      val compC = Component[ComponentNotConstructed, CompA]("CompC", new CompALifeCycle())

      var compRegisteredCount = new AtomicInteger(0)
      var ComponentShutdownEventCount = new AtomicInteger(0)
      val subscriber: Any => Unit = event => {
        event match {
          case e: ComponentStartedEvent => compRegisteredCount.incrementAndGet()
          case e: ComponentShutdownEvent => ComponentShutdownEventCount.incrementAndGet()
        }

        log.info((compRegisteredCount.get() + ComponentShutdownEventCount.get()) + " : " + event)

      }

      var app = Application()
      app.eventBus.subscribe(subscriber, classOf[ComponentStartedEvent]);
      app.eventBus.subscribe(subscriber, classOf[ComponentShutdownEvent]);
      val comps = (compA :: compB :: compC :: Nil)
      app = comps.foldLeft(app) { (app, comp) =>
        log.info("\n" + app + "\n")
        app.register(comp)._1
      }

      app.shutdown()

      Thread.sleep(50l)

      (compRegisteredCount.get() + ComponentShutdownEventCount.get()) should be(comps.size * 2)
    }

    it("will publish events before and after the application is shutdown") {
      val compA = Component[ComponentNotConstructed, CompA]("CompA", new CompALifeCycle())
      val compB = Component[ComponentNotConstructed, CompA]("CompB", new CompALifeCycle())
      val compC = Component[ComponentNotConstructed, CompA]("CompC", new CompALifeCycle())

      var compRegisteredCount = new AtomicInteger(0)
      var ComponentShutdownEventCount = new AtomicInteger(0)
      var PreApplicationShutdownEventCount = new AtomicInteger(0)
      var PostApplicationShutdownEventCount = new AtomicInteger(0)
      val subscriber: Any => Unit = event => {
        event match {
          case e: ComponentStartedEvent => compRegisteredCount.incrementAndGet()
          case e: ComponentShutdownEvent => ComponentShutdownEventCount.incrementAndGet()
          case e: PreApplicationShutdownEvent => PreApplicationShutdownEventCount.incrementAndGet()
          case e: PostApplicationShutdownEvent => PostApplicationShutdownEventCount.incrementAndGet()
        }

        log.info((compRegisteredCount.get() + ComponentShutdownEventCount.get() + PreApplicationShutdownEventCount.get() + PostApplicationShutdownEventCount.get()) + " : " + event)
      }

      var app = Application()
      app.eventBus.subscribe(subscriber, classOf[ComponentStartedEvent]);
      app.eventBus.subscribe(subscriber, classOf[ComponentShutdownEvent]);
      app.eventBus.subscribe(subscriber, classOf[PreApplicationShutdownEvent]);
      app.eventBus.subscribe(subscriber, classOf[PostApplicationShutdownEvent]);
      val comps = (compA :: compB :: compC :: Nil)
      app = comps.foldLeft(app) { (app, comp) =>
        log.info("\n" + app + "\n")
        app.register(comp)._1
      }

      app.shutdown()

      compRegisteredCount.get() should be(comps.size)
      ComponentShutdownEventCount.get() should be(comps.size)
      PreApplicationShutdownEventCount.get() should be(1)
      PostApplicationShutdownEventCount.get() should be(1)
    }
  }

  it("will publish events when a component is stopped") {
    val compA = Component[ComponentNotConstructed, CompA]("CompA", new CompALifeCycle())
    val compAWithShutdownFailure = Component[ComponentNotConstructed, CompA]("CompA-BAD", new CompALifeCycleWithShutdownFailure())
    val compB = Component[ComponentNotConstructed, CompA]("CompB", new CompALifeCycle())
    val compC = Component[ComponentNotConstructed, CompA]("CompC", new CompALifeCycle())

    var compRegisteredCount = 0
    var ComponentShutdownEventCount = 0
    var ComponentShutdownFailedEventCount = 0
    val subscriber: Any => Unit = event => {
      event match {
        case e: ComponentStartedEvent => compRegisteredCount += 1
        case e: ComponentShutdownEvent => ComponentShutdownEventCount += 1
        case e: ComponentShutdownFailedEvent => ComponentShutdownFailedEventCount += 1
      }

      log.info((compRegisteredCount + ComponentShutdownEventCount + ComponentShutdownFailedEventCount) + " : " + event)

    }

    var app = Application()
    app.eventBus.subscribe(subscriber, classOf[ComponentStartedEvent]);
    app.eventBus.subscribe(subscriber, classOf[ComponentShutdownEvent]);
    app.eventBus.subscribe(subscriber, classOf[ComponentShutdownFailedEvent]);
    val comps = (compA :: compB :: compC :: compAWithShutdownFailure :: Nil)
    app = comps.foldLeft(app) { (app, comp) =>
      log.info("\n" + app + "\n")
      app.register(comp)._1
    }

    app.shutdown()

    Thread.sleep(100l)

    compRegisteredCount should be(comps.size)
    ComponentShutdownEventCount should be(comps.size - 1)
    ComponentShutdownFailedEventCount should be(1)
  }

  it("can return the component shutdown order") {
    reverseShutdownOrder = Nil

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

    log.info(comps.mkString("\n\n***************** comps *****************\n", "\n\n", "\n*************************************\n"))

    val app = comps.foldLeft(Application()) { (app, comp) =>
      log.info("\n" + app + "\n")
      app.register(comp)._1
    }

    val appCompShutdownOrder = app.getComponentShutdownOrder.toList

    log.info("*** app components = " + app.components.mkString("\n\n", "\n", "\n\n"))

    val appShutdowned = app.shutdown()

    val shutdownOrder = reverseShutdownOrder.reverse
    log.info("*** shutdownOrder = " + shutdownOrder)

    shutdownOrder.indexOf("CompA") should be < 2
    shutdownOrder.indexOf("CompE") should be < 2
    shutdownOrder.indexOf("CompD") should be(2)
    shutdownOrder.indexOf("CompB") should be(3)
    shutdownOrder.indexOf("CompC") should be(4)

    shutdownOrder match {
      case ("CompE" :: "CompA" :: "CompD" :: "CompB" :: "CompC" :: Nil) =>
      case ("CompA" :: "CompE" :: "CompD" :: "CompB" :: "CompC" :: Nil) =>
      case _ => fail(s"Shutdown order is not correct: $shutdownOrder")
    }

    shutdownOrder should be(appCompShutdownOrder)
  }

}