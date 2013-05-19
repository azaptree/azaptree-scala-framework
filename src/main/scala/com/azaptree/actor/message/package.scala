package com.azaptree.actor

package object message {
  val SUCCESS_MESSAGE_STATUS = MessageStatus()
  val ERROR_MESSAGE_STATUS = MessageStatus(code = 500, "Unexpected Error")
}