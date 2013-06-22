package com.azaptree

import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

package object config {

  type ConfigValidator = Config => Option[Exception]

  lazy val globalConfig: Config = ConfigFactory.load()

}