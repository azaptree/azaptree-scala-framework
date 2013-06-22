package com.azaptree

package object config {

  type ConfigValidator = com.typesafe.config.Config => Option[Exception]

}