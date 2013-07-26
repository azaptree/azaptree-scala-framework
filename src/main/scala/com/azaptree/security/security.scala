package com.azaptree.security

import java.util.Date
import reflect.runtime.universe._
import org.bson.types.ObjectId
import java.util.Objects
import com.azaptree.utils.TypedKey
import com.azaptree.utils.TypedKeyValue
import com.mongodb.casbah.commons.MongoDBObject
import com.azaptree.security.hash.Hash

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

case class HashCredential(override val keyValue: TypedKeyValue[Hash], override val expiresOn: Option[Date] = None) extends Credential[Hash]

/**
 * Private credentials are paired with public credentials with the same name.
 * The intent for public and private credentials is to support public/private keys.
 * For authentication checks, only private credentials are used.
 */
case class Subject(
  principals: Set[Principal[_]],
  privateCredentials: Option[Set[Credential[_]]],
  publicCredentials: Option[Set[Credential[_]]],
  status: SubjectStatus,
  statusTimestamp: Date,
  maxSessions: Int = 1,
  authenticationInfo: AuthenticationInfo)

case class AuthenticationInfo(
  consecutiveAuthenticationFailedCount: Int = 0,
  lastAuthenticationFailure: Option[Date] = None,
  lastAuthenticationSuccess: Option[Date] = None)

sealed trait SubjectStatus

final object LOCKED extends SubjectStatus
final object ACTIVE extends SubjectStatus
final object INACTIVE extends SubjectStatus
final object TERMINATED extends SubjectStatus

//TODO: design permission targets to be scalable and efficient to query

/**
 * if no actions and targets are specified, then the permission applies to the entire domain.
 *
 * Actions and targets are used to apply more fine grained permissions.
 */
case class Permission(domain: String, actions: Option[Set[String]] = None, targets: Option[PermissionTargets] = None)

/**
 *
 */
case class PermissionTargets(targets: Option[Set[ObjectId]] = None, targetsQuery: Option[MongoDBObject] = None) {
  require(targets.isDefined || targetsQuery.isDefined, "Either targets and / or targetsQuery needs to be defined")
}

case class PermissionDefinition(description: String, permission: Permission)

case class PermissionDomain(name: String, permissions: Set[Permission])