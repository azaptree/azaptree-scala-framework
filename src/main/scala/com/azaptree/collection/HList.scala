package com.azaptree.collection

/**
 * Heterogenous typesafe list
 */
sealed trait HList {
  def size: Int
}

final case class HCons[H, T <: HList](head: H, tail: T) extends HList {
  def :+:[T](v: T) = HCons(v, this)

  override def toString = head + " :+: " + tail

  override def size: Int = {
    def size(counter: Int, list: HList): Int = {
      list match {
        case HCons(head, tail) => size(counter + 1, tail)
        case _ => counter
      }
    }

    size(0, this)
  }
}

class HNil extends HList {
  def :+:[T](v: T) = HCons(v, this)

  override val size = 0

  override def toString = "Nil"
}

object HList {
  type :+:[H, T <: HList] = HCons[H, T]

  val :+: = HCons

  val HNil = new HNil
}