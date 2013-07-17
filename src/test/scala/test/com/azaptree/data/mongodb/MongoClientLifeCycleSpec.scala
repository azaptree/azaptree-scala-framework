package test.com.azaptree.data.mongodb

import org.scalatest.matchers.ShouldMatchers
import com.azaptree.application.ApplicationService
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.application.Component
import com.mongodb.casbah.MongoClient
import com.azaptree.data.mongodb.MongoClientLifeCycle
import org.scalatest.FunSpec
import org.slf4j.LoggerFactory
import com.mongodb.casbah.commons.MongoDBObject

class MongoClientLifeCycleSpec extends FunSpec with ShouldMatchers {
  val log = LoggerFactory.getLogger("MongoClientLifeCycleSpec")

  describe("A MongoComponentLifeCycle") {

    it("can be used to create a MongoClient") {
      val applicationService = new ApplicationService()
      val mongoClientLifeCycle = MongoClientLifeCycle()
      val mongoClientComp = new Component[ComponentNotConstructed, MongoClient](name = "mongoClient", componentLifeCycle = mongoClientLifeCycle)
      val mongoClient = applicationService.registerComponent(mongoClientComp)

      try {
        mongoClient foreach { mongoClient =>
          val db = mongoClient("test")
          db.collectionNames.foreach(name => log.info("collection name : {}", name))
          val coll = db("MongoClientLifeCycleSpec")
          val a = MongoDBObject("hello" -> "world")
          val b = MongoDBObject("language" -> "scala")

          coll.insert(a)
          coll.insert(b)

          log.info("coll.count() = {}", coll.count())
          coll.drop()
        }

      } finally {
        applicationService.stop()
      }

      Thread.sleep(10l)

    }

  }

}