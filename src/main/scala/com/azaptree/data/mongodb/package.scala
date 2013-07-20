package com.azaptree.data

import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.MongoDB

//TODO: define healthchecks for mongo databases and collections
package object mongodb {

  def mongoDatabase(database: String)(implicit mongoClient: MongoClient): MongoDB = {
    mongoClient(database)
  }

  def mongoCollection(database: String, collection: String)(implicit mongoClient: MongoClient): MongoCollection = {
    mongoClient(database)(collection)
  }

}