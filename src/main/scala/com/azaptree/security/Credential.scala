package com.azaptree.security

import com.azaptree.utils.TypedKeyValue
import java.util.Date
import com.azaptree.security.hash.Hash

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