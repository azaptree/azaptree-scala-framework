package test.com.azaptree.actors

import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers
import scala.concurrent.Await
import scala.concurrent.Promise

class DataFlowSpec extends FeatureSpec with ShouldMatchers {

  feature("Akka dataflows are supported") {

    scenario("Test out simple data flows") {
      import akka.dataflow._
      import scala.concurrent.ExecutionContext.Implicits.global
      import scala.concurrent.duration._

      val f1 = flow { "Hello world!" }

      Await.result(f1, 1.second) should be("Hello world!")

      val f2 = flow {
        val f1 = flow { "Hello" }
        f1() + " world!"
      }

      Await.result(f2, 1.second) should be("Hello world!")

      val v1, v2 = Promise[Int]()
      val f3 = flow {
        // v1 will become the value of v2 + 10 when v2 gets a value
        v1 << v2() + 10
        v1() + v2()
      }

      flow { v2 << 5 } // As you can see, no blocking above!

      Await.result(f3, 1.second) should be(20)

    }

  }

}