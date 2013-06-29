package com.azaptree.actor.config

import scala.concurrent.duration.Duration
import com.typesafe.config.Config
import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.SupervisorStrategy
import akka.actor.OneForOneStrategy
import akka.actor.Actor
import scala.language.existentials
import akka.actor.ActorPath
import scala.concurrent.duration.FiniteDuration
import akka.routing.Router
import akka.routing.RouterConfig
import akka.actor.PoisonPill

@SerialVersionUID(1L)
case class ActorConfig(
    actorClass: Class[_ <: Actor],
    actorPath: ActorPath,
    routedTo: Boolean = false,
    loggingReceive: Boolean = false,
    supervisorStrategy: Either[SupervisorStrategyConfig, SupervisorStrategy] = Left(SupervisorStrategyConfig()),
    topLevelActor: Boolean = false,
    /**
     * used to provide any Actor specific config
     */
    config: Option[Config] = None,
    /**
     * If specified, then the Actor will be gracefully stopped before the ActorSystem shutdown commences.
     *
     * If not specified, then the Actor will be shutdown upon ActorSystem shutdown.
     */
    gracefulStopTimeout: Option[FiniteDuration] = None,
    /**
     * If specified, then the specified message will sent to the Actor when stopping
     */
    stopMessage: Option[Any] = Some(PoisonPill),
    /**
     * If specified, then the actor will be created using specified dispatcher config.
     *
     * NOTE: dispatcher is in fact a path into the configuration
     */
    dispatcher: Option[String] = None,
    mailbox: Option[String] = None,
    routerConfig: Option[RouterConfig] = None) {

  def name: String = actorPath.name

  def actorOfActorSystem(implicit actorSystem: ActorSystem): ActorRef = {
    ActorConfigRegistry.register(actorSystem.name, this)

    actorSystem.actorOf(props, name)
  }

  def props: Props = {
    val withOption: (Props, Option[String], (Props, String) => Props) => Props = { (props, configPath, f) =>
      configPath match {
        case None => props
        case Some(p) => f(props, p)
      }
    }

    val withRouter: (Props, Option[RouterConfig], (Props, RouterConfig) => Props) => Props = { (props, config, f) =>
      config match {
        case None => props
        case Some(c) => f(props, c)
      }
    }

    val withDispatcher = withOption.curried(Props(actorClass))(dispatcher)(_.withDispatcher(_))
    val withMailbox = withOption.curried(withDispatcher)(dispatcher)(_.withMailbox(_))
    withRouter.curried(withMailbox)(routerConfig)(_.withRouter(_))
  }

  def actorOfActorContext(implicit actorContext: ActorContext): ActorRef = {
    ActorConfigRegistry.register(actorContext.system.name, this)
    actorContext.actorOf(props, name)
  }

  def registerIfTopLevelActor(implicit actorSystem: ActorSystem) = {
    if (topLevelActor) ActorConfigRegistry.register(actorSystem.name, this)
  }
}

@SerialVersionUID(1L)
case class SupervisorStrategyConfig(supervisorStrategyType: SupervisorStrategyType = OneForOne, maxNrOfRetries: Int = -1, withinTimeRange: Duration = Duration.Inf)

sealed trait SupervisorStrategyType

case object OneForOne extends SupervisorStrategyType

case object AllForOne extends SupervisorStrategyType