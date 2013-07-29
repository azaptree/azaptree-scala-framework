package com.azaptree.security

import com.azaptree.utils.TypedKeyValue
import org.bson.types.ObjectId

trait Principal[T] extends Serializable {

  def keyValue: TypedKeyValue[T]

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: Principal[_] =>
        keyValue.key == that.keyValue.key
      case _ => false
    }
  }

  override def hashCode() = { keyValue.key.hashCode() }
}

case class ObjectIdPrincipal(override val keyValue: TypedKeyValue[ObjectId]) extends Principal[ObjectId]

case class UserId(userId: String)

case class UserIdPrincipal(override val keyValue: TypedKeyValue[UserId]) extends Principal[UserId]