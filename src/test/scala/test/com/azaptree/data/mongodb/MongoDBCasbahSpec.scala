package test.com.azaptree.data.mongodb

import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FunSuite
import com.mongodb.casbah.Imports._
import org.slf4j.LoggerFactory
import java.util.UUID
import com.mongodb.casbah.commons.MongoDBObject

class MongoDBCasbahSpec extends FunSuite with ShouldMatchers {
  val log = LoggerFactory.getLogger("MongoDBCasbahSpec")

  test("Verify the MongoDB installation using the Casbah driver") {

    val mongoClient = MongoClient("localhost", 27017)

    val db = mongoClient("test")
    db.collectionNames.foreach(name => log.info("collection name : {}", name))

    try {
      val coll = db("MongoDBCasbahSpec")
      val a = MongoDBObject("hello" -> "world")
      val b = MongoDBObject("language" -> "scala")

      coll.insert(a)
      coll.insert(b)

      log.info("coll.count() = {}", coll.count())

      val allDocs = coll.find()
      log.info("allDocs = {}", allDocs)
      for (doc <- allDocs) log.info("doc = {}", doc)

      val hello = MongoDBObject("hello" -> "world")
      val helloWorld = coll.findOne(hello)
      log.info("helloWorld = {}", helloWorld)

      val goodbye = MongoDBObject("goodbye" -> "world")
      val goodbyeWorld = coll.findOne(goodbye)
      log.info("goodbyeWorld = {}", goodbyeWorld)

      val query = MongoDBObject("language" -> "scala")
      val update = MongoDBObject("platform" -> "JVM")
      val result = coll.update(query, update)

      log.info("Number updated: {}", result.getN)
      for (c <- coll.find) log.info("{}", c)

      val query2 = MongoDBObject("platform" -> "JVM")
      val update2 = $set("language" -> "Scala")
      val result2 = coll.update(query, update)

      log.info("Number updated: " + result2.getN)
      for (c <- coll.find) log.info("{}", c)

      val query3 = MongoDBObject("language" -> "clojure")
      val update3 = $set("platform" -> "JVM")
      val result3 = coll.update(query, update, upsert = true)

      log.info("Number updated: " + result3.getN)
      for (c <- coll.find) log.info("{}", c)

      val query4 = MongoDBObject("language" -> "clojure")
      val result4 = coll.remove(query)

      log.info("Number removed: " + result4.getN)
      for (c <- coll.find) log.info("{}", c)

      val query5 = MongoDBObject()
      val result5 = coll.remove(query)

      log.info("Number removed: " + result5.getN)
      log.info("coll.count() = {}", coll.count())

    } finally {
      db("MongoDBCasbahSpec").drop()
    }

  }

  test("array $slice keeps the last N elements in the array") {
    val mongoClient = MongoClient("localhost", 27017)

    val db = mongoClient("test")
    db.collectionNames.foreach(name => log.info("collection name : {}", name))

    try {
      val coll = db("MongoDBCasbahSpec")
      val numberList = 1 :: 2 :: 3 :: Nil
      var a = MongoDBObject("numberList" -> numberList)
      a = a + ("id" -> UUID.randomUUID())
      val result = coll.insert(a)

      val query = MongoDBObject("id" -> a("id"))
      coll.find(query).foreach { a =>
        log.info("an object with an array: {}", a)
      }

      import scala.language.reflectiveCalls

      val arrayUpdate = MongoDBObject(("$each" -> Array(4, 5, 6)), ("$slice" -> new Integer(-3)))
      var update = MongoDBObject("$push" -> MongoDBObject("numberList" -> arrayUpdate))

      coll.update(query, update)

      coll.find(query).foreach { a =>
        log.info("an object with an array: {}", a)
      }

    } finally {
      db("MongoDBCasbahSpec").drop()
    }
  }

}