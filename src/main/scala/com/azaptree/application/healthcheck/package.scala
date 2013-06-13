package com.azaptree.application

import scala.concurrent.Future
import com.azaptree.application.healthcheck.HealthCheck
import com.azaptree.application.healthcheck.HealthCheckResult

package object healthcheck {

  type HealthCheckRunner = (HealthCheck) => Future[HealthCheckResult]
}