package test.com.azaptree.security.hash.mongodb

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.data.mongodb.MongoDBEntity
import com.azaptree.entity.Entity
import com.azaptree.security.hash.HashService
import com.azaptree.security.hash.mongodb.HashServiceConverter
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll
import com.azaptree.data.mongodb.Database
import com.azaptree.data.mongodb.Collection
import com.azaptree.data.mongodb.MongoDBEntityRegistry
import scala.reflect.runtime.universe._
import com.mongodb.casbah.Imports._
import com.azaptree.data.mongodb.MongoClientLifeCycle
import com.azaptree.application.ApplicationService
import com.azaptree.application.ApplicationService
import com.azaptree.application.Component
import com.mongodb.casbah.MongoClient
import com.azaptree.application.ComponentNotConstructed
import com.azaptree.security.hash.HashService
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import org.slf4j.LoggerFactory
import com.azaptree.security.hash.mongodb.HashServiceRepository
import scala.util.Success
import scala.util.Failure
import com.azaptree.security.hash.HashRequest
import com.azaptree.security.hash.HashArrayRequest

class HashServiceRepositorySpec extends FunSpec with ShouldMatchers with BeforeAndAfterAll {
  val log = LoggerFactory.getLogger(getClass())

  val applicationService = new ApplicationService()
  val mongoClientComp = new Component[ComponentNotConstructed, MongoClient](name = "mongoClient", componentLifeCycle = MongoClientLifeCycle())
  implicit val mongoClient = applicationService.registerComponent(mongoClientComp).get

  val hashServiceMongoDBEntity = MongoDBEntity[Entity[HashService]](Database("test"), Collection("HashServiceRepositorySpec"), HashServiceConverter)
  MongoDBEntityRegistry.register(hashServiceMongoDBEntity)
  hashServiceMongoDBEntity.entityCollection().drop()

  val hashServiceRepository = HashServiceRepository(hashServiceMongoDBEntity)

  override def afterAll(configMap: Map[String, Any]) {
    MongoDBEntityRegistry(typeOf[Entity[HashService]]).foreach { entity =>
      entity.entityCollection().drop()
    }
  }

  describe("A HashServiceRepository") {
    it("can persist HashServices to MongoDB") {
      MongoDBEntityRegistry(typeOf[Entity[HashService]]) match {
        case Some(e) =>
          val mongoDBEntity = e.asInstanceOf[MongoDBEntity[Entity[HashService]]]
          val hashService = HashService(MessageDigestAlgorithms.SHA_256)

          hashServiceRepository.insert(hashService) match {
            case Success(entity) =>
              hashServiceRepository.findByEntityId(entity.entityId) match {
                case Success(r) => r match {
                  case Some(hashServiceEntity) =>
                    assert(hashServiceEntity.entityId == entity.entityId)
                    val hashService = hashServiceEntity.entity
                    val hashRequest = HashArrayRequest("A HashServiceRepository can persist HashServices to MongoDB".getBytes())
                    hashService.computeHash(hashRequest) match {
                      case Success(hash) =>
                        log.info("created hash using HashService that is defined within database : {}", hash.toBase64)
                        val hashService2 = hashServiceRepository.findByEntityId(entity.entityId).get.get.entity
                        val hash2 = hashService2.computeHash(hashRequest).get
                        assert(hash2 == hash)
                      case Failure(e) => throw e
                    }
                  case None => throw new IllegalStateException("Failed to find entity for : " + entity.entityId)
                }
                case Failure(e) => throw e
              }
            case Failure(exception) => throw exception
          }
        case None => throw new IllegalStateException("Entity[HashService] type was not found in MongoDBEntityRegistry")
      }
    }

  }

}