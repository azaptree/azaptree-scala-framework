package com.azaptree.application.healthcheck

import com.typesafe.config.Config

case class HealthCheck(info: HealthCheckInfo, config: HealthCheckConfig)

case class HealthCheckInfo(group: String = "", name: String)(displayName: String = s"$group:$name", description: String)

case class HealthCheckConfig(
  enabled: Boolean = true,
  importanceLevel: Int = 5,
  greenRange: HeathCheckIndicatorScoreRange,
  yellowRange: HeathCheckIndicatorScoreRange,
  redRange: HeathCheckIndicatorScoreRange,
  config: Option[Config])

case class HeathCheckIndicatorScoreRange(indicator: HealthCheckIndicator, minScore: Int, maxScore: Int)

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

case class StopWatch(start: Long = System.currentTimeMillis, end: Long = 0) {
  def stop() = {
    copy(end = System.currentTimeMillis)
  }

  def executionTimeMillis = end - start
}

