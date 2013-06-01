package test.com.azaptree.actors

import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers

class ActorFSMSpec extends FeatureSpec with ShouldMatchers {

  feature("""An ActorFSM will support the Actor lifecycle. The Actor starts in the "Constructed" state when created. 
      |When in the "Constructed" state, an "Initialize" message will trigger a transition over to the "Idle" state.
      |Upon the transition from "Constructed" -> "Idle", the Actor can perform intialization.
      |When in the "Idle" state, a "Start" message will trigger a transition over to the "Running" state.
      |When in the "Running" state, a "Stop" message will trigger a transtion over to the "Idle" state.
      |Application Messages received while in the "Running" state will be processed. 
      |Application messages received while in the "Constructed" or "Idle" state will be stashed.
      |Upon transitioning over from "Idle" to the "Running" state, any stashed Application Messages will be unstashed. 
      |When a "Destroy" message is received, the Actor will be stopped. 
      |Any other message types other than com.azaptree.actors.fsm.State or com.azaptree.actors.message.Message will be dropped. """.stripMargin) {

    scenario("""Create a new ActorFSM and monitor its state transitions. The initial state should be "Constructed" """) {
      pending
    }

    scenario("""Create a new ActorFSM and monitor its state transitions. Send State messages that will trigger the following transitions
        |Constructed --Initialize-->  Idle
        |Idle --Start--> Running  
        |Running --Stop--> Idle
        |Idle --Start--> Running
        |Running --Destroy--> Destroyed 
        |Idle --Destroy--> Destroyed 
        |Constructed --Destroy--> Destroyed""".stripMargin) {
      pending
    }

    //TODO

  }

}