package com.azaptree.application

import scala.concurrent.Future
import com.azaptree.application.healthcheck.HealthCheck
import com.azaptree.application.healthcheck.HealthCheckResult

package object healthcheck {

  /**
   * The function signature is very basic by design.
   * Any other required dependencies for the HealthCheckRunner can be "injected" in using function currying, closures, and Scala functional programming capabilities.
   */
  type HealthCheckRunner = (HealthCheck) => Future[HealthCheckResult]

  type ApplicationHealthCheck = Tuple2[HealthCheck, HealthCheckRunner]
}