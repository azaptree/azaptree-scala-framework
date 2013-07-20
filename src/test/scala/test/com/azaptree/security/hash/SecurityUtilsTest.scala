package test.com.azaptree.security.hash

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.slf4j.LoggerFactory
import com.azaptree.application.healthcheck.StopWatch
import com.azaptree.security._
import com.sun.org.apache.xml.internal.security.utils.Base64
import java.security.SecureRandom

class SecurityUtilsTest extends FunSuite with ShouldMatchers {
  val log = LoggerFactory.getLogger(getClass())

  test("com.azaptree.security.nextRandomBytes()") {
    for (i <- 1 to 10) {
      val stopWatch = new StopWatch()
      log.info("{} : {} : {} msec", i, Array(Base64.encode(nextRandomBytes()), stopWatch.stop().executionTimeMillis.get))
    }
  }

  test("SecureRandom.generateSeed(32) is slow") {
    val secureRandom = new SecureRandom()
    for (i <- 1 to 2) {
      val stopWatch = new StopWatch()
      log.info("{} : {} : {} msec", i, Array(Base64.encode(secureRandom.generateSeed(32)), stopWatch.stop().executionTimeMillis.get))
    }
  }

}