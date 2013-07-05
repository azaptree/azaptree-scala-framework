package com.azaptree.entity

import java.util.UUID
import java.util.Objects
import org.bson.types.ObjectId

/**
 * Used to track entity versions.
 *
 */
class VersionedEntity[+A](
  override val entityId: ObjectId = new ObjectId(),
  override val entity: A,
  val entityVersion: ObjectId = new ObjectId())
    extends Entity[A](entityId, entity) {

  require(entityVersion != null, "entityVersion is required")

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

  /**
   * returns a new version with a new entityVersion and sets the entityUpdatedOn to the current timestamp set to the new entity
   */
  def newVersion[B >: A](newEntity: B): VersionedEntity[B] = {
    require(newEntity != null)
    new VersionedEntity[B](
      entityId = this.entityId,
      entityVersion = new ObjectId(),
      entity = newEntity)
  }
}

