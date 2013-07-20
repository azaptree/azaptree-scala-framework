package com.azaptree.security

import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.commons.MongoDBObject
import com.azaptree.security.hash.HashService
import com.mongodb.casbah.Imports._

package object hash {

  def hashService2MongoDBObject(hashService: HashService): MongoDBObject = {
    val builder = MongoDBObject.newBuilder
    builder += "algorithm" -> hashService.hashAlgorithm
    builder += "salt" -> hashService.privateSalt
    builder.result()
  }

}