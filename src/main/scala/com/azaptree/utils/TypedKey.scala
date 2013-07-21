package com.azaptree.utils
import reflect.runtime.universe._
import java.util.Objects

case class TypedKey[T: TypeTag](name: String) extends Serializable {
  def keyType: Type = typeOf[T]

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: TypedKey[_] =>
        name == that.name && keyType =:= that.keyType
      case _ => false
    }
  }

  override def hashCode() = { Objects.hash(name, keyType.toString()) }
}

case class TypedKeyValue[T](val key: TypedKey[T], val value: T) extends Serializable {

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: TypedKeyValue[_] =>
        key == that.key
      case _ => false
    }
  }

  override def hashCode() = { key.hashCode() }
}