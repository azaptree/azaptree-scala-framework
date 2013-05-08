package test.com.azaptree.entity

import org.scalatest.FeatureSpec
import org.scalatest.matchers.ShouldMatchers

import com.azaptree.entity.VersionedEntity

class VersionedEnitySpec extends FeatureSpec with ShouldMatchers {

  class Foo {}

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
}