package com.couchmate.api.ws.protocol

import enumeratum._

sealed trait CreateNewAccountError extends EnumEntry

object CreateNewAccountError
  extends Enum[CreateNewAccountError]
  with PlayJsonEnum[CreateNewAccountError] {
  val values = findValues

  case object EmailExists     extends CreateNewAccountError
  case object UsernameExists  extends CreateNewAccountError
  case object UnknownError    extends CreateNewAccountError
}
