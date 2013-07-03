package com.azaptree.http

case class HttpServiceConfig(https: Boolean = false, host: String, port: Int = 80, serviceName: String, serviceVersion: String) {

  /**
   * http{s}://${host}:${port}/service/${serviceName}/${serviceVersion}
   *
   */
  def baseUrl: String = {
    val protocol = if (https) "https" else "http"
    s"${protocol}://${host}:${port}/service/${serviceName}/${serviceVersion}"
  }
}
