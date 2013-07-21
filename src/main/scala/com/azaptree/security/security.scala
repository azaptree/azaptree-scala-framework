package com.azaptree.security

import java.util.Date
import reflect.runtime.universe._
import org.bson.types.ObjectId
import java.util.Objects
import com.azaptree.utils.TypedKey
import com.azaptree.utils.TypedKeyValue

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

trait Credential[T] extends Serializable {
  def keyValue: TypedKeyValue[T]

  def expiresOn: Option[Date] = None

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: Credential[_] =>
        keyValue.key == that.keyValue.key
      case _ => false
    }
  }

  override def hashCode() = { keyValue.key.hashCode() }
}

case class Subject(
  principals: Set[Principal[_]],
  privateCredentials: Option[Set[Credential[_]]],
  publicCredentials: Option[Set[Credential[_]]],
  status: SubjectStatus,
  statusTimestamp: Date,
  maxSessions: Int = 1)

case class AuthenticationInfo(
  consecutiveAuthenticationFailedCount: Int = 0,
  lastAuthenticationFailure: Option[Date] = None,
  lastAuthenticationSuccess: Option[Date] = None)

sealed trait SubjectStatus

final object LOCKED extends SubjectStatus
final object ACTIVE extends SubjectStatus
final object INACTIVE extends SubjectStatus
final object TERMINATED extends SubjectStatus