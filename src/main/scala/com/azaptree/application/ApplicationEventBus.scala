package com.azaptree.application

import akka.event.EventBus
import akka.event.japi.SubchannelEventBus
import akka.event.japi.LookupEventBus

class ApplicationEventBus extends LookupEventBus[Any, Any => Unit, Class[_]] {

  override def classify(event: Event): Classifier = event.getClass

  override def compareSubscribers(a: Subscriber, b: Subscriber): Int = {
    a.getClass().getName().compare(b.getClass().getName())
  }

  override def publish(event: Event, subscriber: Subscriber) = subscriber(event)

  override def mapSize(): Int = 16

}