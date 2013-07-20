package test.com.azaptree.data.mongodb

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.data.mongodb.MongoDBEntity
import com.azaptree.entity.Entity
import com.azaptree.data.mongodb.MongoDBObjectConverter
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.azaptree.data.mongodb.MongoDBEntityRegistry
import org.slf4j.LoggerFactory
import reflect.runtime.universe._
import com.azaptree.security.hash.HashService

class HashServiceMongoDBObjectConverter extends MongoDBObjectConverter[Entity[HashService]] {
  def convert(entity: Entity[HashService]): com.mongodb.casbah.commons.MongoDBObject = {
    val mongoDBObj = MongoDBObject()
    mongoDBObj
  }

  def convert(mongoDBObject: com.mongodb.casbah.commons.MongoDBObject): Entity[HashService] = {
    new Entity[HashService](entity = HashService())
  }
}

class MongoDBEntityRegistrySpec extends FunSpec with ShouldMatchers {
  val log = LoggerFactory.getLogger(getClass())

  describe("A MongoDBEntityRegistry") {
    it("is used to register MongoDBEntity's") {
      import reflect.runtime.universe._

      val entity = MongoDBEntity[Entity[HashService]]("test", "user", new HashServiceMongoDBObjectConverter())

      MongoDBEntityRegistry.register[Entity[HashService]](entity)
      val registeredTypes = MongoDBEntityRegistry.registeredTypes
      registeredTypes.foreach { registeredType =>
        log.info("registeredType : {}", registeredType)
      }

      val key = typeOf[Entity[HashService]]
      val userEntity2 = MongoDBEntityRegistry(key)

    }
  }

}