package com.azaptree.application

import akka.event.japi.SubchannelEventBus
import akka.util.Subclassification
import akka.event.japi.LookupEventBus
import scala.concurrent.duration.DurationConversions.Classifier
import scala.collection.mutable.Subscriber
import scala.concurrent.ExecutionContext

/**
 * Event are published to Subscribers synchronously on the publisher's thread
 */
class SynchronousSubchannelEventBus extends SubchannelEventBus[Any, Any => Unit, Class[_]] {
  object ClassSubclassification extends Subclassification[Classifier] {
    override def isEqual(x: Class[_], y: Class[_]): Boolean = x == y

    override def isSubclass(x: Class[_], y: Class[_]): Boolean = y.isAssignableFrom(x)

  }

  override def classify(event: Event): Classifier = event.getClass

  override def publish(event: Event, subscriber: Subscriber) = subscriber(event)

  override implicit def subclassification: Subclassification[Classifier] = ClassSubclassification

}

/**
 * Event are published to Subsribers asynchronously on the publisher's thread
 */
class AsynchronousSubchannelEventBus extends SynchronousSubchannelEventBus {

  override def publish(event: Event, subscriber: Subscriber) = ExecutionContext.global.execute(new Runnable() { def run = subscriber(event) })

}