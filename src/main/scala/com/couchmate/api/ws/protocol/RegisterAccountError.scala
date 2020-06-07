package com.couchmate.api.ws.protocol

import enumeratum._

sealed trait RegisterAccountError extends EnumEntry

object RegisterAccountError
  extends Enum[RegisterAccountError]
  with PlayJsonEnum[RegisterAccountError] {
  val values = findValues

  case object EmailExists     extends RegisterAccountError
  case object UsernameExists  extends RegisterAccountError
  case object UnknownError    extends RegisterAccountError
}
