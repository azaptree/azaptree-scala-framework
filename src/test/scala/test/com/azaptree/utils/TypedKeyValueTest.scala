package test.com.azaptree.utils

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.utils.TypedKey
import com.azaptree.utils.TypedKeyValue
import reflect.runtime.universe._
import com.azaptree.utils.TypedKeyValue
import org.slf4j.LoggerFactory

class TypedKeyValueTest extends FunSuite with ShouldMatchers {
  val log = LoggerFactory.getLogger(getClass())

  test("TypedKey can be used as a Map key") {
    var map = Map[TypedKey[_], TypedKeyValue[_]]()

    val typedKeyValue1 = TypedKeyValue[String](TypedKey[String]("a"), "a value")
    val typedKeyValue2 = TypedKeyValue[String](TypedKey[String]("b"), "b value")
    val typedKeyValue3 = TypedKeyValue[List[String]](TypedKey[List[String]]("b"), "b value" :: Nil)

    map += (typedKeyValue1.key -> typedKeyValue1)
    map += (typedKeyValue2.key -> typedKeyValue2)
    map += (typedKeyValue3.key -> typedKeyValue3)

    map.values.foreach(log.info("{}", _))

    assert(map.size == 3)

    map(typedKeyValue1.key) == typedKeyValue1
    map(typedKeyValue2.key) == typedKeyValue2
    map(typedKeyValue3.key) == typedKeyValue3
  }

  test("TypedKey equals will return true and hascodes are equal for 2 separate instances created with the same key-value pair") {
    val typedKeyValue1 = TypedKeyValue[String](TypedKey[String]("a"), "a value")
    val typedKeyValue2 = TypedKeyValue[String](TypedKey[String]("a"), "a value")

    assert(typedKeyValue1 == typedKeyValue2)
    assert(typedKeyValue1.## == typedKeyValue2.##)

    val typedKeyValue3 = TypedKeyValue[List[String]](TypedKey[List[String]]("b"), "b value" :: Nil)
    val typedKeyValue4 = TypedKeyValue[List[String]](TypedKey[List[String]]("b"), "b value" :: Nil)

    assert(typedKeyValue3 == typedKeyValue4)
    assert(typedKeyValue3.## == typedKeyValue4.##)

    assert(typedKeyValue1 != typedKeyValue3)
  }

}