package test.com.azaptree.actors.message

import akka.testkit.TestKit
import akka.testkit.ImplicitSender
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import akka.testkit.DefaultTimeout
import org.scalatest.FeatureSpec
import akka.actor.ActorSystem
import scala.reflect.internal.util.StripMarginInterpolator

class MessageLoggingSpec(_system: ActorSystem) extends TestKit(_system)
  with DefaultTimeout with ImplicitSender
  with FeatureSpec with ShouldMatchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("MessageLoggingSpec"))

  override def afterAll() = {
    system.shutdown()
  }

  feature("An ActorSystem can be configured to log application messages to a database") {
    scenario("Send an Actor some messages, then try to find them in the database") {
      pending
    }
  }

  feature("An ActorSystem can be configured to log DeadLetter messages") {
    scenario("Send a msg to a non-existent Actor, which will cause the message to be published as a DeadLetter") {
      pending
    }
  }

  feature("The ActorSystem can be configured to log the dead letters to a database") {
    scenario("""Send a msg to a non-existent Actor, which will cause the message to be published as a DeadLetter. 
        |Then search for the dead letters in the database""".stripMargin) {
      pending
    }
  }
}