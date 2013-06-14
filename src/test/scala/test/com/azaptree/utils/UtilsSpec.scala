package test.com.azaptree.utils

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.utils.StringBuilderWriter
import java.io.StringWriter
import java.io.PrintWriter

class UtilsSpec extends FunSpec with ShouldMatchers {

  describe("getExceptionStackTrace()") {
    it("returns the exception's stacktrace as a string") {
      import com.azaptree.utils._

      val ex1 = new Exception("First Exception")
      val ex2 = new IllegalStateException("TESTING getExceptionStackTrace()", ex1)

      val stackTrace: String = getExceptionStackTrace(ex2)

      val sw = new StringWriter()
      val pw = new PrintWriter(sw)
      ex2.printStackTrace(pw)

      sw.toString() should be(stackTrace)
    }
  }

  describe("A StringBuilderWriter") {
    it("can be used to generate Strings") {
      val sw = new StringBuilderWriter()
      val pw = new PrintWriter(sw)
      pw.print("Alfio ")
      pw.print("Zappala")
      sw.toString should be("Alfio Zappala")
    }
  }

}