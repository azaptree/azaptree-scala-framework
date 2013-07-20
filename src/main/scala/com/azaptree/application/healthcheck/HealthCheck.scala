package com.azaptree.application.healthcheck

import com.typesafe.config.Config
import scala.concurrent.Future
import com.azaptree.application.ApplicationService
import scala.concurrent.ExecutionContext
import com.azaptree.concurrent.TaskSchedule

case class HealthCheck(info: HealthCheckInfo, config: HealthCheckConfig)

case class HealthCheckInfo(group: String = "", name: String)(displayName: String = s"$group:$name", description: String)

case class HealthCheckConfig(
    enabled: Boolean = true,
    importanceLevel: Int = 5,
    greenRange: GreenHeathCheckIndicatorThreshold = GreenHeathCheckIndicatorThreshold(90),
    yellowRange: YellowHeathCheckIndicatorThreshold = YellowHeathCheckIndicatorThreshold(75),
    redRange: RedHeathCheckIndicatorThreshold = RedHeathCheckIndicatorThreshold(0),
    config: Option[Config] = None,
    schedule: Option[TaskSchedule] = None) {

  def computeHealthCheckIndicator(healthScore: Int): HealthCheckIndicator = {
    if (healthScore >= greenRange.minScore) {
      GREEN
    } else if (healthScore >= yellowRange.minScore) {
      YELLOW
    } else {
      RED
    }
  }

}

sealed trait HeathCheckIndicatorThreshold {
  val indicator: HealthCheckIndicator
  val minScore: Int
}

case class GreenHeathCheckIndicatorThreshold(minScore: Int) extends HeathCheckIndicatorThreshold {
  val indicator = GREEN
}

case class YellowHeathCheckIndicatorThreshold(minScore: Int) extends HeathCheckIndicatorThreshold {
  val indicator = YELLOW
}

case class RedHeathCheckIndicatorThreshold(minScore: Int) extends HeathCheckIndicatorThreshold {
  val indicator = RED
}

sealed trait HealthCheckIndicator

case object GREEN extends HealthCheckIndicator
case object YELLOW extends HealthCheckIndicator
case object RED extends HealthCheckIndicator

case class HealthCheckResult(
  healthCheck: HealthCheck,
  stopWatch: StopWatch,
  healthScore: Int,
  healthCheckIndicator: HealthCheckIndicator,
  info: Option[String] = None,
  exceptionStackTrace: Option[String] = None)

case class StopWatch(start: Long = System.currentTimeMillis, end: Option[Long] = None) {
  def stop() = {
    copy(end = Some(System.currentTimeMillis))
  }

  def reset() = {
    StopWatch()
  }

  def executionTimeMillis: Option[Long] = end.map(_ - start)
}

