package test.com.azaptree.entity

import java.util.UUID

import scala.collection.immutable.HashSet

import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers

import com.azaptree.entity.Entity

class EntityFeatureSpec extends FeatureSpec with ShouldMatchers {

  class Foo()

  feature("An Entity's entityId will be universally unique - entityId type is UUID") {
    scenario("Create 1000 new entities and none should have the same entityId") {
      var entityIds = new HashSet[UUID]()
      for (i <- 1 to 1000) {
        entityIds = entityIds + new Entity[Foo](entity = new Foo).entityId
      }

      entityIds.size should equal(1000)
    }
  }

  feature("When creating new instances for an entity, if the entityId or createdOn is not provided at construction time, then default values will be assigned automatically") {
    scenario("Create an instance without specifying any values except for entity") {
      val foo1 = new Entity[Foo](entity = new Foo)
      foo1.entityId should not be null
      foo1.entityCreatedOn should be <= System.currentTimeMillis

      val uuid = UUID.randomUUID
      val foo2 = new Entity[Foo](entity = new Foo, entityId = uuid)
      foo2.entityId should be(uuid)
      foo2.entityCreatedOn should be <= System.currentTimeMillis
    }

    scenario("Create an Entity with a null entity value should not be allowed") {
      intercept[IllegalArgumentException] {
        val foo1 = new Entity[Foo](entity = null)
      }
    }

    scenario("Create an Entity with a null entityId value should not be allowed") {
      intercept[IllegalArgumentException] {
        val foo1 = new Entity[Foo](entity = new Foo, entityId = null)
      }
    }
  }

  feature("Two entities are considered to be equal if they have the same entityId") {
    scenario("2 new created instances with no entityId specified will never be equal") {
      val foo1 = new Entity[Foo](entity = new Foo)
      val foo2 = new Entity[Foo](entity = new Foo)
      foo1 should not equal (foo2)
    }

    scenario("2 instances created using the same entity id will be equal") {
      val uuid = UUID.randomUUID
      val foo1 = new Entity[Foo](entity = new Foo, entityId = uuid)
      val foo2 = new Entity[Foo](entity = new Foo, entityId = uuid)
      foo1 should equal(foo2)
    }
  }

  feature("Two entities that are equal will always have the same hashCode") {
    scenario("2 instances created using the same entityId will have the same hashCode") {
      val uuid = UUID.randomUUID
      val foo1 = new Entity[Foo](entity = new Foo, entityId = uuid)
      val foo2 = new Entity[Foo](entity = new Foo, entityId = uuid)
      foo1.## should equal(foo2.##)
    }

    scenario("2 newly created instances will not have the same hashCode") {
      val foo1 = new Entity[Foo](entity = new Foo)
      val foo2 = new Entity[Foo](entity = new Foo)
      foo1.## should not equal (foo2.##)
    }
  }

  feature("When creating a new Entity, the entity value is required") {
    scenario("Create an entity with a null entity value") {
      intercept[IllegalArgumentException] {
        val foo1 = new Entity[Foo](entity = null)
      }
    }
  }

}