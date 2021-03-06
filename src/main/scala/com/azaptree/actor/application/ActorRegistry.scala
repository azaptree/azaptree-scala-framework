package com.azaptree.actor.application

import com.azaptree.actor.message.Message
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.actorRef2Scala
import akka.actor.Terminated
import akka.actor.Terminated
import com.azaptree.actor.message.MessageProcessor

class ActorRegistry extends MessageProcessor {
  import ActorRegistry._

  var registeredActors = Map.empty[ActorPath, ActorRef]

  override def receiveMessage = {
    case Message(m: RegisterActor, _) =>
      registeredActors += (m.actor.path -> m.actor)
      context.watch(m.actor)
    case Message(t: Terminated, _) => registeredActors -= t.actor.path

    case Message(GetRegisteredActors(actorPath), _) => sender ! getRegisteredActors(actorPath)
    case Message(GetRegisteredActor(actorPath), _) => sender ! getRegisteredActor(actorPath)
    case Message(GetRegisteredActorCount(actorPath), _) => sender ! getRegisteredActorCount(actorPath)
  }

  def getRegisteredActors(actorPath: Option[ActorPath]): Message[RegisteredActors] = {
    actorPath match {
      case None => Message(RegisteredActors(actorPath, registeredActors.values))
      case Some(a) =>
        val path = a.toString()
        val actors = registeredActors.values.filter(a => {
          val currentPath = a.path.toString()
          currentPath.equals(path) || (currentPath.startsWith(path) && currentPath.charAt(path.length()) == '/')
        })
        Message(RegisteredActors(actorPath, actors))
    }
  }

  def getRegisteredActor(actorPath: ActorPath): Message[Option[ActorRef]] = {
    Message[Option[ActorRef]](registeredActors.get(actorPath))
  }

  def getRegisteredActorCount(actorPath: Option[ActorPath]): Message[RegisteredActorCount] = {
    actorPath match {
      case None => Message(RegisteredActorCount(actorPath, registeredActors.size))
      case Some(a) =>
        val path = a.toString()
        Message(RegisteredActorCount(actorPath, registeredActors.keys.count(_.toString().startsWith(path))))
    }
  }

}

object ActorRegistry {
  val ACTOR_NAME = "ActorRegistry"

  val ACTOR_PATH = s"/user/$ACTOR_NAME"

  sealed trait ActorRegistryMessage

  sealed trait ActorRegistryRequest extends ActorRegistryMessage

  case class RegisterActor(actor: ActorRef) extends ActorRegistryRequest

  /**
   * if actorPath = None, then all registered ActorPaths are returned.
   * Otherwise, the subtree of ActorPaths starting at the specified ActorPath is returned
   *
   */
  case class GetRegisteredActors(actorPath: Option[ActorPath] = None) extends ActorRegistryRequest

  /**
   * response message type is Message[Option[ActorRef]]
   */
  case class GetRegisteredActor(actorPath: ActorPath) extends ActorRegistryRequest

  case class GetRegisteredActorCount(actorPath: Option[ActorPath] = None) extends ActorRegistryRequest

  sealed trait ActorRegistryResponse

  case class RegisteredActors(actorPath: Option[ActorPath] = None, actors: Iterable[ActorRef]) extends ActorRegistryResponse

  case class RegisteredActorCount(actorPath: Option[ActorPath] = None, count: Int) extends ActorRegistryResponse
}

