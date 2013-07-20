package com.azaptree.security.hash

import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.MessageDigestAlgorithms
import com.azaptree.security._
import java.io.InputStream
import java.security.MessageDigest
import org.apache.commons.codec.digest.DigestUtils
import scala.util.Try
import java.io.ByteArrayInputStream
import java.security.DigestInputStream
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.util.Arrays

case class Hash(hash: Array[Byte], hashParams: HashParams) {

  def toBase64(): String = {
    Base64.encodeBase64String(hash)
  }

  def toHex(): String = {
    Hex.encodeHexString(hash)
  }

  def isEmpty(): Boolean = {
    hash == null || hash.length == 0
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: Hash =>
        Arrays.equals(hash, that.hash) && hashParams == that.hashParams
      case _ => false
    }
  }
}

case class HashService(hashAlgorithm: String = MessageDigestAlgorithms.SHA_256, privateSalt: Array[Byte] = nextRandomBytes()) {
  val log = LoggerFactory.getLogger(getClass())

  require(privateSalt != null && privateSalt.length > 0, "privateSalt is required")

  def computeHash(hashRequest: HashRequest): Try[Hash] = {
    Try {
      val digest = MessageDigest.getInstance(hashAlgorithm)
      digest.reset()
      val combinedSalt = privateSalt ++ hashRequest.params.salt
      if (log.isDebugEnabled()) { log.debug("combinedSalt: {}", Base64.encodeBase64String(combinedSalt)) }
      digest.update(combinedSalt)

      var hashed = hashRequest match {
        case r: HashArrayRequest =>
          digest.digest(r.byteSource)
        case _ =>
          val digestInputStream = new DigestInputStream(hashRequest.source, digest)
          val byteBuffer = Array.ofDim[Byte](512)
          var byteCount = 0
          var bytesRead = 0
          while (bytesRead != -1) {
            bytesRead = digestInputStream.read(byteBuffer)
            if (bytesRead > 0) {
              byteCount += bytesRead
            }
            log.debug("byteCount = {}", byteCount)
          }
          digest.digest()
      }

      if (log.isDebugEnabled()) { log.debug("hashed: {}", Base64.encodeBase64String(hashed)) }
      for (i <- 2 to hashRequest.params.hashIterations) {
        digest.reset()
        hashed = digest.digest(hashed)
      }

      Hash(hashed, hashRequest.params)
    }
  }
}

case class HashParams(
    hashAlgorithm: String = MessageDigestAlgorithms.SHA_256,
    hashIterations: Int = 1024 * 128,
    salt: Array[Byte] = nextRandomBytes()) {
  require(hashIterations > 0, "hashIterations must be > 0")
  require(salt.length > 0, "salt cannot be empty")

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: HashParams =>
        hashAlgorithm == that.hashAlgorithm &&
          hashIterations == that.hashIterations &&
          Arrays.equals(salt, that.salt)
      case _ => false
    }
  }
}

trait HashRequest {
  def source: InputStream

  def params: HashParams = HashParams()
}

case class HashArrayRequest(byteSource: Array[Byte], override val params: HashParams = HashParams()) extends HashRequest {
  require(byteSource.length > 0, "byteSource cannot be empty")

  override def source = new ByteArrayInputStream(byteSource)

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: HashArrayRequest =>
        Arrays.equals(byteSource, that.byteSource) && params == that.params
      case _ => false
    }
  }
}

case class HashInputStreamRequest(override val source: InputStream, override val params: HashParams = HashParams()) extends HashRequest
