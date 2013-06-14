package com.azaptree.application

import scala.concurrent.ExecutionContext

/**
 * Event are published to Subsribers asynchronously on the publisher's thread
 */
class AsynchronousSubchannelEventBus extends SynchronousSubchannelEventBus {

  override def publish(event: Event, subscriber: Subscriber) = {
    ExecutionContext.global.execute(new Runnable() { def run = subscriber(event) })
  }

}