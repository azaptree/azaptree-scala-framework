package com.azaptree

import org.bson.types.ObjectId
import java.security.SecureRandom

package object security {

  val secureRandom = new SecureRandom()
  secureRandom.setSeed(secureRandom.generateSeed(32))

  def getPrincipal[A <: Principal[_]](principal: Principal[_]): A = {
    principal.asInstanceOf[A]
  }

  /**
   * The very first call is expensive - takes about 10 seconds because of the secureRandom.generateSeed(32)
   *
   */
  def nextRandomBytes(numBytes: Int = 32): Array[Byte] = {
    val bytes = Array.ofDim[Byte](numBytes)
    secureRandom.nextBytes(bytes)
    bytes
  }

}