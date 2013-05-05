package test.com.azaptree.entity

import java.util.UUID
import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.entity.Entity

class EntityFeatureSpec extends FeatureSpec with ShouldMatchers {

  class Foo(val entityId: UUID = UUID.randomUUID, val entityCreatedOn: Long = System.currentTimeMillis) extends Entity {

  }

  feature("An Entity's entityId will be universally unique - entityId type is UUID") {
    scenario("When creating new instances for an entity, if the entityId is not provided at construction time, then a new one will be assigned automatically") {
      val foo1 = new Foo
      foo1.entityId should not be null
      foo1.entityCreatedOn should be <= System.currentTimeMillis

      val uuid = UUID.randomUUID
      val foo2 = new Foo(entityId = uuid)
      foo2.entityId should be(uuid)
      foo2.entityCreatedOn should be <= System.currentTimeMillis
    }
  }

  feature("An Entity keeps track of when it is first created") {
    scenario("When an Entity is created, the createdOn property can be supplied. If it is not supplied, then it will be assigned the current time") {
      pending
    }
  }

  feature("Two entities are considered to be equal if they have the same entityId") {
    scenario("2 new created instances with no entityId specified will never be equal") {
      val foo1 = new Foo
      val foo2 = new Foo
      foo1 should not equal (foo2)
    }

    scenario("2 instances created using the same entity id will be equal") {
      val uuid = UUID.randomUUID
      val foo1 = new Foo(entityId = uuid)
      val foo2 = new Foo(entityId = uuid)
      foo1 should equal(foo2)
    }
  }

  feature("Two entities that are equal will always have the same hashCode") {
    scenario("2 instances created using the same entityId will have the same hashCode") {
      val uuid = UUID.randomUUID
      val foo1 = new Foo(entityId = uuid)
      val foo2 = new Foo(entityId = uuid)
      foo1.## should equal(foo2.##)
    }

    scenario("2 newly created instances will not have the same hashCode") {
      val foo1 = new Foo
      val foo2 = new Foo
      foo1.## should not equal (foo2.##)
    }
  }

}