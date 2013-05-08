package com.azaptree.entity

import java.util.UUID
import java.util.Objects

class VersionedEntity[+A](
  override val entityId: UUID = UUID.randomUUID,
  override val entityCreatedOn: Long = System.currentTimeMillis,
  val entityVersion: UUID = UUID.randomUUID,
  val entityUpdatedOn: Long = System.currentTimeMillis,
  override val entity: A)
    extends Entity[A](entityId, entityCreatedOn, entity) {

  override def canEqual(that: Any) = that.isInstanceOf[VersionedEntity[A]]

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: VersionedEntity[A] =>
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