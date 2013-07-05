package test.com.azaptree.entity

import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers
import com.azaptree.entity.VersionedEntity
import java.util.UUID
import org.bson.types.ObjectId

class VersionedEnitySpec extends FeatureSpec with ShouldMatchers {

  case class Bar(name: String) {}

  class Foo {}

  feature("When creating new instances for an entity, if the entityId or createdOn is not provided at construction time, then default values will be assigned automatically") {
    scenario("Create an instance without specifying any values except for entity") {
      val foo1 = new VersionedEntity[Foo](entity = new Foo)
      foo1.entityId should not be null
      foo1.entityVersion should not be null
      foo1.entityId.getTime() should be <= System.currentTimeMillis

      val uuid = new ObjectId()
      val foo2 = new VersionedEntity[Foo](entity = new Foo, entityId = uuid)
      foo2.entityId should be(uuid)
      foo2.entityId.getTime() should be <= System.currentTimeMillis
    }

    scenario("Create an Entity with a null entity value should not be allowed") {
      intercept[IllegalArgumentException] {
        val foo1 = new VersionedEntity[Foo](entity = null)
      }
    }

    scenario("Create an Entity with a null entityVersion value should not be allowed") {
      intercept[IllegalArgumentException] {
        val foo1 = new VersionedEntity[Foo](entity = new Foo, entityVersion = null)
      }
    }
  }

  feature("A VersionedEntity is identified by its entityId and entityVersion") {
    scenario("2 VersionEntities with the same entityId but different entityVersions should not be equal") {
      val foo1 = new VersionedEntity[Foo](entity = new Foo)
      foo1 should not equal (new VersionedEntity[Foo](entity = new Foo, entityId = foo1.entityId))
    }

    scenario("2 VersionEntities with the same entityId and entityVersion are considered equal") {
      val foo1 = new VersionedEntity[Foo](entity = new Foo)
      foo1 should equal(new VersionedEntity[Foo](entity = new Foo, entityId = foo1.entityId, entityVersion = foo1.entityVersion))
    }
  }

  feature("Two entities that are equal will always have the same hashCode") {
    scenario("2 instances created using the same entityId will have the same hashCode") {
      val foo1 = new VersionedEntity[Foo](entity = new Foo)
      val foo2 = new VersionedEntity[Foo](entity = new Foo, entityId = foo1.entityId, entityVersion = foo1.entityVersion)
      foo1.## should equal(foo2.##)
    }

    scenario("2 newly created instances will not have the same hashCode") {
      val foo1 = new VersionedEntity[Foo](entity = new Foo)
      val foo2 = new VersionedEntity[Foo](entity = new Foo)
      foo1.## should not equal (foo2.##)
    }

    scenario("2 instances with the same entityId but different entityVersion will not have the same hashCode") {
      val foo1 = new VersionedEntity[Foo](entity = new Foo)
      val foo2 = new VersionedEntity[Foo](entity = new Foo, entityId = foo1.entityId)
      foo1.## should not equal (foo2.##)
    }
  }

  feature("A new version of an existing VersionedEntity can be created by supplying a new entity") {
    scenario("Supply a new entity") {
      val bar1 = new VersionedEntity[Bar](entity = Bar("bar1"))
      val bar2 = bar1.newVersion(Bar("bar2"))
      bar2.entity.name should be("bar2")
    }

    scenario("Supplying a null value as the new value will throw an IllegalArgumentException") {
      val bar1 = new VersionedEntity[Bar](entity = Bar("bar1"))
      intercept[IllegalArgumentException] {
        bar1.newVersion(null)
      }
    }
  }
}