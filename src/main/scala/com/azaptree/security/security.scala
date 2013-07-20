package com.azaptree.security

import java.util.Date

import org.bson.types.ObjectId

trait Principal[T] extends Serializable {
  def principalType: PrincipalType

  def name: String

  def value: T
}

trait PrincipalType

case object OBJECT_ID extends PrincipalType
case object USER_ID extends PrincipalType

case class ObjectIdPrincipal(override val name: String, override val value: ObjectId) extends Principal[ObjectId] {
  override val principalType = OBJECT_ID
}

case class UserIdPrincipal(override val name: String, override val value: String) extends Principal[String] {
  override val principalType = OBJECT_ID
}

trait Credential {
  def credentialType: CredentialType

  def name: String

  def value: Array[Byte]

  def expiresOn: Option[Date] = None

}

trait CredentialType