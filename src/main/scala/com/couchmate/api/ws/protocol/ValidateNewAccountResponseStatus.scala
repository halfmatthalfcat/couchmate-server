package com.couchmate.api.ws.protocol

import enumeratum._

sealed trait ValidateNewAccountResponseStatus extends EnumEntry

object ValidateNewAccountResponseStatus
  extends Enum[ValidateNewAccountResponseStatus]
  with PlayJsonEnum[ValidateNewAccountResponseStatus] {
  val values = findValues

  case object Valid           extends ValidateNewAccountResponseStatus
  case object EmailExists     extends ValidateNewAccountResponseStatus
  case object UsernameExists  extends ValidateNewAccountResponseStatus
  case object UnknownError    extends ValidateNewAccountResponseStatus
}