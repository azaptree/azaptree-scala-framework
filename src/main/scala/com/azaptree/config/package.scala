package com.azaptree

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import com.typesafe.config.ConfigRenderOptions

package object config {

  type ConfigValidator = Config => Option[Exception]

  lazy val globalConfig: Config = ConfigFactory.load()

  val jsonFormattedRenderOptions = ConfigRenderOptions.defaults().setComments(false).setOriginComments(false)

  val jsonRenderOptions = jsonFormattedRenderOptions.setFormatted(false)

  def toJson(config: Config): String = {
    config.root().render(jsonRenderOptions)
  }

  def toFormattedJson(config: Config): String = {
    config.root().render(jsonFormattedRenderOptions)
  }

}