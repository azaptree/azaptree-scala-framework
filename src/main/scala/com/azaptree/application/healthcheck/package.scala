package com.azaptree.application

import scala.concurrent.Future
import com.azaptree.application.healthcheck.HealthCheck
import com.azaptree.application.healthcheck.HealthCheckResult
import scala.concurrent.ExecutionContext

package object healthcheck {

  /**
   * The function signature is very basic by design.
   * Any other required dependencies for the HealthCheckRunner can be "injected" in using function currying, closures, and Scala functional programming capabilities.
   */
  type HealthCheckRunner = (HealthCheck, ApplicationService) => Future[HealthCheckResult]

  type HealthCheckScorer = (HealthCheck, ApplicationService) => (Int, Option[String])

  type ApplicationHealthCheck = Tuple2[HealthCheck, HealthCheckRunner]

  def healthCheckRunner(scorer: HealthCheckScorer)(implicit executionContext: ExecutionContext): HealthCheckRunner = {
    val runner: HealthCheckRunner = (healthCheck, appService) => {
      Future[HealthCheckResult] {
        val start = StopWatch()
        try {
          val (healthScore, info) = scorer(healthCheck, appService)
          val end = start.stop()
          val healthCheckIndicator = healthCheck.config.computeHealthCheckIndicator(healthScore)
          HealthCheckResult(healthCheck = healthCheck,
            stopWatch = end,
            healthScore = healthScore,
            healthCheckIndicator = healthCheckIndicator,
            info = info)
        } catch {
          case ex: Exception =>
            import com.azaptree.utils._

            HealthCheckResult(healthCheck = healthCheck,
              stopWatch = start.stop(),
              healthScore = 0,
              healthCheckIndicator = RED,
              exceptionStackTrace = Some(getExceptionStackTrace(ex)))
        }
      }
    }

    runner
  }
}