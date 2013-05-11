package com.azaptree.entity

import java.util.UUID

/**
 * Wrapper class that provides a consistent view of a persistent entity. The Entity separates entity specific fields from the domain specific model.
 * In other words, in order for a class to be an entity, it does not need to extend any class or mixin any trait. Simply, provide an Entity wrapper for it.
 */
class Entity[+A](val entityId: UUID = UUID.randomUUID, val entityCreatedOn: Long = System.currentTimeMillis, val entity: A) extends Serializable with Equals {
  require(entityId != null, "entityId is required")
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

