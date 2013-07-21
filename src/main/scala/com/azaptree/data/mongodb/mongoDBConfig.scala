package com.azaptree.data.mongodb

import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObjectBuilder

trait MongoDBObjectConverter[A] {
  def convert(entity: A): MongoDBObject

  def convert(mongoDBObject: MongoDBObject): A
}

case class Database(name: String)

case class Collection(name: String)

/**
 * This assumes that each collection only stores data that at the very least, is supported by the specified converter.
 *
 */
case class MongoDBEntity[A](
    database: Database,
    collection: Collection,
    converter: MongoDBObjectConverter[A],
    indexes: Option[Iterable[Index]] = None) {

  def entityCollection()(implicit mongoClient: MongoClient): MongoCollection = mongoCollection(database.name, collection.name)
}

case class Index(fields: Iterable[IndexField], unique: Boolean = false, sparse: Boolean = false) {

  def ensureIndex(background: Boolean = false)(coll: MongoCollection): Unit = {
    var keysBuilder = new MongoDBObjectBuilder()
    fields.foreach { field =>
      keysBuilder += field.name -> { if (field.ascending) 1 else -1 }
    }
    val keys = keysBuilder.result()

    var optionsBuilder = new MongoDBObjectBuilder()
    optionsBuilder += "unique" -> unique
    optionsBuilder += "sparse" -> sparse
    val options = optionsBuilder.result()

    coll.ensureIndex(keys, options)
  }
}

case class IndexField(name: String, ascending: Boolean = true)

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