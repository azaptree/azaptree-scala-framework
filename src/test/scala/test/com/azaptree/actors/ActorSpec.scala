package test.com.azaptree.actors

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FeatureSpec

class ActorSpec extends FeatureSpec with ShouldMatchers {

  feature("""Actors will keep track of counts for total number of messages processed successfully and messages processed unsucessfully. 
Actors will also track the last time a message was processed successfully, and the last time a message processing failure occurred""") {

    scenario("""Create a new Actor and send some Hearbeat messages. 
Then check that number of messages successfully processed matches the number of Heartbeat messages that were sent""") {
      pending
    }

  }

}