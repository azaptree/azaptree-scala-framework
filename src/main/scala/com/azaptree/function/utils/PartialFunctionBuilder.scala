package com.azaptree.function.utils

class PartialFunctionBuilder[A, B] {
  import scala.collection.immutable.Vector

  type PF = PartialFunction[A, B]

  private[this] var pfsOption: Option[Vector[PF]] = Some(Vector.empty)

  private[this] var processMessage: Option[PF] = None

  private def mapPfs[C](f: Vector[PF] ⇒ (Option[Vector[PF]], C)): C = {
    pfsOption.fold(throw new IllegalStateException("Already built"))(f) match {
      case (newPfsOption, result) ⇒ {
        pfsOption = newPfsOption
        result
      }
    }
  }

  def +=(pf: PF): Unit =
    mapPfs { case pfs ⇒ (Some(pfs :+ pf), ()) }

  def result: PF = {
    processMessage.getOrElse {
      assert(pfsOption.isDefined && pfsOption.get.size > 0, "At least one PartialFunction is required")
      val pf = mapPfs { case pfs ⇒ (None, pfs.foldLeft[PF](Map.empty) { _ orElse _ }) }
      processMessage = Some(pf)
      pf
    }
  }
}