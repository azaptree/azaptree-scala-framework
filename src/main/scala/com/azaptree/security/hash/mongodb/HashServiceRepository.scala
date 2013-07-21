package com.azaptree.security.hash.mongodb

import com.azaptree.data.mongodb.MongoDBEntity
import com.azaptree.entity.Entity
import com.azaptree.security.hash.HashService
import com.mongodb.casbah.MongoClient
import com.azaptree.security.hash.HashService
import com.mongodb.casbah.Imports._
import scala.util.Try
import com.azaptree.data.mongodb.MongoDBObjectConverter
import com.azaptree.data.mongodb._
import com.mongodb.casbah.commons.MongoDBObjectBuilder
import com.azaptree.security.hash.HashService

case object HashServiceConverter extends MongoDBObjectConverter[Entity[HashService]] {
  def convert(entity: Entity[HashService]): MongoDBObject = {
    var builder = new MongoDBObjectBuilder()
    builder = addObjectId(builder, entity.entityId)
    val hashService = entity.entity
    builder += "name" -> hashService.name
    builder += "algorithm" -> hashService.hashAlgorithm
    builder += "salt" -> hashService.privateSalt
    builder.result()
  }

  def convert(mongoDBObject: MongoDBObject): Entity[HashService] = {
    val name = mongoDBObject.as[String]("name")
    val algorithm = mongoDBObject.as[String]("algorithm")
    val privateSalt = mongoDBObject.as[Array[Byte]]("salt")
    val hashService = HashService(name, algorithm, privateSalt)
    new Entity[HashService](mongoDBObject._id.get, hashService)
  }
}

case class HashServiceRepository(hashServiceEntity: MongoDBEntity[Entity[HashService]])(implicit mongoClient: MongoClient) {

  def insert(hashService: HashService): Try[Entity[HashService]] = {
    Try {
      val entity = new Entity[HashService](entity = hashService)
      val collection = hashServiceEntity.entityCollection
      val mongoDBObj = hashServiceEntity.converter.convert(entity)
      collection.insert(mongoDBObj)
      entity
    }
  }

}