package com.azaptree.data.mongodb

import com.mongodb.casbah.MongoClient
import com.azaptree.application.ComponentLifeCycle
import com.azaptree.application.ComponentInitialized
import com.azaptree.application.Component
import com.azaptree.application.ComponentConstructed
import com.azaptree.application.ComponentNotConstructed
import com.mongodb.MongoClientOptions
import com.mongodb.MongoClientURI
import com.azaptree.application.ComponentStarted
import com.azaptree.application.ComponentStopped
import com.azaptree.logging.Slf4jLogger

case class MongoClientLifeCycle(mongoClientUri: String = "mongodb://localhost:27017", mongoClientOptionsBuilder: Option[MongoClientOptions.Builder] = None) extends ComponentLifeCycle[MongoClient] with Slf4jLogger {

  override protected def create(comp: Component[ComponentNotConstructed, MongoClient]): Component[ComponentConstructed, MongoClient] = {
    val clientUri = mongoClientOptionsBuilder match {
      case Some(builder) =>
        new MongoClientURI(mongoClientUri, builder)
      case None => new MongoClientURI(mongoClientUri)
    }

    val mongoClient = MongoClient(clientUri)
    log.info(s"mongoClient{version : ${mongoClient.getVersion()}, writeConcern : ${mongoClient.getWriteConcern.getW()}, serverAddress : ${mongoClient.address}")
    comp.copy[ComponentConstructed, MongoClient](componentObject = Some(mongoClient))
  }

  override protected def stop(comp: Component[ComponentStarted, MongoClient]): Component[ComponentStopped, MongoClient] = {
    comp.componentObject foreach { mongoClient =>
      mongoClient.close()
    }

    comp.copy[ComponentStopped, MongoClient](componentObject = None)
  }

}