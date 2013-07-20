package test.com.azaptree.data.mongodb

import java.util.Date
import java.util.UUID
import org.scalatest.FunSuite
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers
import org.slf4j.LoggerFactory
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import java.io.File

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
      for (doc <- allDocs) {
        log.info("doc = {}", doc)
        val mongoDBObj: MongoDBObject = doc
        log.info("hello -> {}", mongoDBObj.getAs[String]("hello"))
      }

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
      mongoClient.close()
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
      mongoClient.close()
    }
  }

  test("ObjectId can be used to search on object creation time") {
    val mongoClient = MongoClient("localhost", 27017)

    val db = mongoClient("test")
    db.collectionNames.foreach(name => log.info("collection name : {}", name))

    try {
      val coll = db("MongoDBCasbahSpec")

      val start = new ObjectId(new Date())
      for (i <- 1 to 10) {
        coll.insert(MongoDBObject("createdOn" -> System.currentTimeMillis()))
      }

      val start2 = new ObjectId(new Date())
      for (i <- 1 to 10) {
        coll.insert(MongoDBObject("createdOn" -> System.currentTimeMillis()))
      }

      val start3 = new ObjectId(new Date())
      for (i <- 1 to 10) {
        coll.insert(MongoDBObject("createdOn" -> System.currentTimeMillis()))
      }

      val rs = coll.find("_id" $gte start)
      log.info("rs.size = {}", rs.size)
      rs.size should be(30)

      val rs2 = coll.find("_id" $gte start2)
      log.info("rs2.size = {}", rs2.size)
      rs2.size should be(20)

      val rs3 = coll.find("_id" $gte start3)
      log.info("rs3.size = {}", rs3.size)
      rs3.size should be(10)

      val rs4 = coll.find("_id" $gte start2 $lt start3)
      log.info("rs4.size = {}", rs4.size)
      rs4.size should be(10)

    } finally {
      db("MongoDBCasbahSpec").drop()
      mongoClient.close()
    }

  }

  test("Testing out GridFS") {
    import java.io.FileInputStream
    import com.mongodb.casbah.Imports._
    import com.mongodb.casbah.gridfs.Imports._

    // Connect to the database
    val mongoClient = MongoClient()("test")

    // Pass the connection to the GridFS class
    val gridfs = GridFS(mongoClient)

    // Save a file to GridFS
    val file = new File("src/test/resources/application.conf")
    val fis = new FileInputStream(file)
    val id = gridfs(fis) { f =>
      f.filename = file.getAbsolutePath()
      f.contentType = "text/xml"
    }
    val fileId = id.get.asInstanceOf[ObjectId]

    try {
      // Find a file in GridFS by its ObjectId
      val myFileById = gridfs.findOne(fileId)
      myFileById.isDefined should be(true)

      // Print all filenames stored in GridFS
      for (f <- gridfs) log.info(f.filename.get)

      // Or find a file in GridFS by its filename
      val myFileByName = gridfs.findOne(file.getAbsolutePath())
      myFileByName.isDefined should be(true)
    } finally {
      // Print all filenames stored in GridFS
      for (gridfsFile <- gridfs) {
        gridfs.remove(gridfsFile._id.get)
      }
    }

  }

}