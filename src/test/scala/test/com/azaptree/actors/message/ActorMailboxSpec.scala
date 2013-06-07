package test.com.azaptree.actors.message

import org.scalatest.BeforeAndAfterAll
import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers

import akka.actor.ActorSystem
import akka.testkit.DefaultTimeout
import akka.testkit.ImplicitSender
import akka.testkit.TestKit

class ActorMailboxSpec(_system: ActorSystem) extends TestKit(_system)
    with DefaultTimeout with ImplicitSender
    with FeatureSpec with ShouldMatchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("ActorMailboxSpec"))

  override def afterAll() = {
    system.shutdown()
  }

  feature("The mailbox size, i.e., the number of messages queued in a mailbox, can be inspected at runtime") {
    scenario("""1. Create an Actor with Stash, and send it messages. 
        |2. Check the mailbox size. 
        |3. Send the Actor a message to unstash the messages. Then confirm that the mailbox has been flushed.""".stripMargin) {
      pending
    }
  }
}