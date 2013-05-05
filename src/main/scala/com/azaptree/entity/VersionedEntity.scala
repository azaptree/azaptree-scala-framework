package com.azaptree.entity

import java.util.UUID
import java.util.Objects

trait VersionedEntity extends Entity {
  val entityVersion: UUID;
  val entityUpdatedOn: Long;

  override def canEqual(that: Any) = that.isInstanceOf[VersionedEntity]

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: VersionedEntity =>
        if (this eq that) {
          true
        } else {
          (that canEqual this) && (entityId == that.entityId) && (entityVersion == that.entityVersion)
        }
      case _ => false
    }
  }

  override def hashCode(): Int = Objects.hash(entityId, entityVersion)
}