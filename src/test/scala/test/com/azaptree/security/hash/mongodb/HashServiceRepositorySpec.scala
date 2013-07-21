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

class HashServiceRepositorySpec extends FunSpec with ShouldMatchers with BeforeAndAfterAll {

  override def afterAll(configMap: Map[String, Any]) {

  }

  val hashServiceMongoDBEntity = MongoDBEntity[Entity[HashService]](Database("test"), Collection("HashServiceRepositorySpec"), HashServiceConverter)

  describe("A HashServiceRepository") {
    it("can persist HashServices to MongoDB") {

    }

  }

}