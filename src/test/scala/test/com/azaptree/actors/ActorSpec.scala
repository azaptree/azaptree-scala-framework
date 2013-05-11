package test.com.azaptree.actors

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FeatureSpec

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

}