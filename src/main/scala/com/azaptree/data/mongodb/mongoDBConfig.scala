package com.azaptree.data.mongodb

import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.MongoCollection

trait MongoDBObjectConverter[A] {
  def convert(entity: A): com.mongodb.casbah.commons.MongoDBObject

  def convert(mongoDBObject: com.mongodb.casbah.commons.MongoDBObject): A
}

/**
 * This assumes that each collection only stores data that at the very least, is supported by the specified converter
 */
case class MongoDBEntity[A](
    database: String,
    collection: String,
    converter: MongoDBObjectConverter[A]) {

  def entityCollection(implicit mongoClient: MongoClient): MongoCollection = mongoCollection(database, collection)
}

object MongoDBEntityRegistry {
  import reflect.runtime.universe._

  var entityConfigMap = Map.empty[Type, MongoDBEntity[_]]

  def register[A: TypeTag](entity: MongoDBEntity[A]) = {
    val key = typeOf[A]
    entityConfigMap += (key -> entity)
  }

  def registeredTypes(): Iterable[Type] = entityConfigMap.keys

  def apply(entityType: Type) = entityConfigMap.get(entityType)

}