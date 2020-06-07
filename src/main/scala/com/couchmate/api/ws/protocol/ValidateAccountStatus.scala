package com.couchmate.api.ws.protocol

import enumeratum._

sealed trait ValidateAccountStatus extends EnumEntry

object ValidateAccountStatus
  extends Enum[ValidateAccountStatus]
  with PlayJsonEnum[ValidateAccountStatus] {
  val values = findValues

  case object Valid           extends ValidateAccountStatus
  case object EmailExists     extends ValidateAccountStatus
  case object UsernameExists  extends ValidateAccountStatus
  case object UnknownError    extends ValidateAccountStatus
}