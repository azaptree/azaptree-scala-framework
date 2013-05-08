package com.azaptree.entity

import java.util.UUID

class Entity[+A](val entityId: UUID = UUID.randomUUID, val entityCreatedOn: Long = System.currentTimeMillis, val entity: A) extends Serializable with Equals {
  require(entity != null, "entity is required")

  override def canEqual(that: Any) = that.isInstanceOf[Entity[A]]

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: Entity[A] =>
        if (Entity.this eq that) {
          true
        } else {
          (that canEqual Entity.this) && (entityId == that.entityId)
        }
      case _ => false
    }
  }

  override def hashCode(): Int = entityId.hashCode
}

