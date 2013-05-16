package test.com.azaptree.actors.message

import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers

class ActorSpec extends FeatureSpec with ShouldMatchers {

  feature("""Actors will keep track of counts for total number of messages processed successfully and messages processed unsucessfully. 
      Actors will also track the last time a message was processed successfully, and the last time a message processing failure occurred.
      When an Actor receives a Message[GetStats] message, it will return a Message[MessageStats] to the sender.
      Heartbeat and GetStats messages do not count against MessageStats. However, the last time a heartbeat message was received will be tracked.""") {

    scenario("""Create a new Actor and send some application messages. 
        Then check that number of messages successfully processed matches the number of Heartbeat messages that were sent
        Verify that lastSuccessOn has been updated.""") {
      pending
    }

    scenario("""Create a new Actor and send some Hearbeat messages. 
        Then check that number of messages successfully processed has not been incremented.
        Verify that lastHeartbeatOn has been updated.""") {
      pending
    }

    scenario("""Create a new Actor and send some messages that will trigger failures. 
        Then check that number of messages unsuccessfully processed matches the number of messages that were sent. 
        Also verify that lastFailureOn has been updated.""") {
      pending
    }

  }

  feature("""The ActorConfig can be requested by sending a message to the Actor""") {
    scenario("Send an Actor a GetConfig message") {
      pending
    }
  }

  feature("An Actor will log all messages that are received with processing metrics") {
    scenario("Send an Actor some application messages and check that they are logged.") {
      pending
    }

    scenario("Send an Actor some system messages and check that they are logged.") {
      pending
    }
  }

  feature("An ActorSystem can be configured to log to a database") {
    scenario("Send an Actor some messages, then try to find them in the database") {
      pending
    }
  }

  feature("An ActorSystem can be configured to log all DeadLetters") {
    scenario("Send a msg to a non-existent Actor, which will cause the message to be sent to published as a DeadLetter") {
      pending
    }

    scenario("The ActorSystem is configured to log the dead letters to a database") {
      pending
    }
  }

  feature("Actors will publish MessageProcessedEvents to the ActorSystem's event stream") {
    scenario("""Create an Actor that subscribes to MessageEvents. 
        Then send messages to another Actor, and check that MessageEvent's are published for each message """) {
      pending
    }
  }

  feature("""Actors will register with an ActorRegistry as when started/restarted and unregister when stopped""") {
    scenario("Create an Actor and check that is has registered") {
      pending
    }

    scenario("Restart an Actor and check that is has unregistered and registered") {
      pending
    }

    scenario("Stop an Actor and check that is has unregistered") {
      pending
    }
  }

  feature("The mailbox size, i.e., the number of messages queued in a mailbox, can be inspected at runtime") {
    scenario("""1. Create an Actor with Stash, and send it messages. 
        2. Check the mailbox size. 
        3. Send the Actor a message to unstash the messages. Then confirm that the mailbox has been flushed.""") {
      pending
    }
  }

  feature("An Actor defines the messages it supports within its companion object") {
    scenario("Send messages to the Actor using message types defined within its companion object") {
      pending
    }

  }

}