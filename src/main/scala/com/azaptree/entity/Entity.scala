package com.azaptree.entity

import java.util.UUID

trait Entity extends Serializable with Equals {
  val entityId: UUID;
  val entityCreatedOn: Long;

  override def canEqual(that: Any) = that.isInstanceOf[Entity]

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: Entity =>
        if (this eq that) {
          true
        } else {
          (that canEqual this) && (entityId == that.entityId)
        }
      case _ => false
    }
  }

  override def hashCode(): Int = entityId.hashCode
}

