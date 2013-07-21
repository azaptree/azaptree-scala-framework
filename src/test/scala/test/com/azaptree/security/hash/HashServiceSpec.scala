package test.com.azaptree.security.hash

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.security.hash.HashParams
import com.azaptree.security.hash.HashService
import com.azaptree.security.hash.HashArrayRequest
import org.slf4j.LoggerFactory
import com.azaptree.application.healthcheck.StopWatch
import com.azaptree.security.hash.Hash
import org.apache.commons.codec.binary.Base64
import com.azaptree.security.hash.HashService
import com.azaptree.security.hash.HashRequest
import com.azaptree.security.hash.HashInputStreamRequest
import java.io.ByteArrayInputStream

class HashServiceSpec extends FunSpec with ShouldMatchers {
  val log = LoggerFactory.getLogger(getClass())

  describe("A HashService") {
    it("can hash an input Array[Byte] source") {
      log.info("using default HashParams")
      var stopWatch = new StopWatch()
      val hashParams1 = HashParams()
      stopWatch = stopWatch.stop()
      log.info("hashParams1 create time = {} msec", stopWatch.executionTimeMillis.get)
      stopWatch = stopWatch.reset()
      val hashService1 = new HashService("HashServiceSpec")
      stopWatch = stopWatch.stop()
      log.info("hashService1 create time = {} msec", stopWatch.executionTimeMillis.get)
      stopWatch = stopWatch.reset()

      var hashes = Set[Hash]()
      for (i <- 1 to 10) {
        log.info("{} : hash an array input source", i)
        stopWatch = stopWatch.reset()
        val hashRequest1 = HashArrayRequest("ARRAY-SOURCE-1".getBytes())
        stopWatch = stopWatch.stop()
        log.info("hashRequest1 time = {} msec", stopWatch.executionTimeMillis.get)
        stopWatch = stopWatch.reset()
        val hash1 = hashService1.computeHash(hashRequest1)
        hashes.contains(hash1.get) should be(false)
        hashes = hashes + hash1.get
        stopWatch = stopWatch.stop()
        log.info("computeHash time = {} msec", stopWatch.executionTimeMillis.get)

        hash1.isSuccess should be(true)
        hash1.get.hashParams should equal(hashRequest1.params)
        log.info("hash1.hash.base64 = {}", hash1.get.toBase64)
        log.info("hash1.hash.hex = {}", hash1.get.toHex)
      }

    }

    it("can hash an InputStream source") {
      log.info("using default HashParams")
      var stopWatch = new StopWatch()
      val hashParams1 = HashParams()
      stopWatch = stopWatch.stop()
      log.info("hashParams1 create time = {} msec", stopWatch.executionTimeMillis.get)
      stopWatch = stopWatch.reset()
      val hashService1 = new HashService("HashServiceSpec")
      stopWatch = stopWatch.stop()
      log.info("hashService1 create time = {} msec", stopWatch.executionTimeMillis.get)
      stopWatch = stopWatch.reset()

      var hashes = Set[Hash]()
      for (i <- 1 to 10) {
        log.info("{} : hash an array input source", i)
        stopWatch = stopWatch.reset()
        val hashRequest1 = HashInputStreamRequest(new ByteArrayInputStream("ARRAY-SOURCE-1".getBytes()))
        stopWatch = stopWatch.stop()
        log.info("hashRequest1 time = {} msec", stopWatch.executionTimeMillis.get)
        stopWatch = stopWatch.reset()
        val hash1 = hashService1.computeHash(hashRequest1)
        hashes.contains(hash1.get) should be(false)
        hashes = hashes + hash1.get
        stopWatch = stopWatch.stop()
        log.info("computeHash time = {} msec", stopWatch.executionTimeMillis.get)

        hash1.isSuccess should be(true)
        hash1.get.hashParams should equal(hashRequest1.params)
        log.info("hash1.hash.base64 = {}", hash1.get.toBase64)
        log.info("hash1.hash.hex = {}", hash1.get.toHex)
      }
    }

    it("reproduce the same hash for the same HashRequest using the same HashService") {
      log.info("using default HashParams")

      val hashService1 = new HashService("HashServiceSpec")

      info("hashing the same request should produce the same hash")
      val hashRequest1 = HashArrayRequest("ARRAY-SOURCE-1".getBytes())
      val hash1 = hashService1.computeHash(hashRequest1)
      log.info("hash1.hash.base64 = {}", hash1.get.toBase64)
      log.info("hash1.hash.hex = {}", hash1.get.toHex)
      hash1.get.toBase64 should be(Base64.encodeBase64String(hash1.get.hash))
      for (i <- 1 to 10) {
        val hash2 = hashService1.computeHash(hashRequest1)
        hash2.isSuccess should be(true)
        log.info("hash2.hash.base64 = {}", hash2.get.toBase64)
        log.info("hash2.hash.hex = {}", hash2.get.toHex)
        hash2.get.toBase64() should be(hash1.get.toBase64())
        hash2.get.toHex() should be(hash1.get.toHex())

        hash2.get should be(hash1.get)
      }

      val hashRequest2 = HashInputStreamRequest(new ByteArrayInputStream("ARRAY-SOURCE-1".getBytes()), hashRequest1.params)
      val hash2 = hashService1.computeHash(hashRequest2)
      hash2.get.toBase64() should be(hash1.get.toBase64())
      hash2.get.toHex() should be(hash1.get.toHex())
      hash2.get should be(hash1.get)
    }

  }

}