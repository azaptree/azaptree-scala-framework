package com.azaptree.actor.registry

import com.azaptree.actor.message.Message
import com.azaptree.actor.message.MessageActor

import javax.naming.OperationNotSupportedException

class ActorRegistry extends MessageActor {

  override def processMessage = {
    case _ => throw new OperationNotSupportedException
  }

}