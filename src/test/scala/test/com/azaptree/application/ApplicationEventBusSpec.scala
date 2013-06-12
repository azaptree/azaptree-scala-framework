package test.com.azaptree.application

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.application.AsynchronousSubchannelEventBus
import java.util.concurrent.atomic.AtomicInteger
import com.azaptree.application.SynchronousSubchannelEventBus

class ApplicationEventBusSpec extends FunSpec with ShouldMatchers {

  describe("An AsynchronousSubchannelEventBus") {
    it("can publish events asynchronously on multiple threads") {
      val eventbus = new AsynchronousSubchannelEventBus()

      @volatile
      var threadIds = Set[String]()

      val eventCount = new AtomicInteger(0)

      eventbus.subscribe(event => {
        val threadName = Thread.currentThread().getName()
        threadIds += threadName
        eventCount.incrementAndGet()
      }, classOf[Any])

      for (i <- 1 to 100) {
        eventbus.publish("i")
      }

      Thread.sleep(100l)

      eventCount.get() should be(100)
      threadIds.size should be > (1)

    }
  }

  describe("An SynchronousSubchannelEventBus") {
    it("will publish events synchronously on the publisher's thread") {
      val eventbus = new SynchronousSubchannelEventBus()

      @volatile
      var threadIds = Set[String]()

      val eventCount = new AtomicInteger(0)

      eventbus.subscribe(event => {
        val threadName = Thread.currentThread().getName()
        threadIds += threadName
        eventCount.incrementAndGet()
      }, classOf[Any])

      for (i <- 1 to 100) {
        eventbus.publish("i")
      }

      Thread.sleep(200l)

      eventCount.get() should be(100)
      threadIds.size should be(1)

    }
  }

}