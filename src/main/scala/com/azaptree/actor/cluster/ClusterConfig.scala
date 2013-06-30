package com.azaptree.actor.cluster

/**
 * if no seedNodes are specified, then this implies: akka.cluster.auto-join = off.
 * Then the node meeds to join manually, using JMX or Command Line Management.
 *
 * Joining can also be performed programatically with Cluster(system).join(address)
 *
 */
case class ClusterConfig(
  actorSystemName: String,
  seedNodes: Option[Set[SeedNode]] = None,
  actorProviderClassName: String = "akka.cluster.ClusterActorRefProvider",
  autoDown: Boolean = true,
  roles: Option[Set[String]] = None)

case class SeedNode(host: String, port: Int = 2552)

case class RemoteConfig(
  logRemoteLifeCycleevents: Boolean = false,
  host: String = "127.0.0.1",
  port: Int = 0)