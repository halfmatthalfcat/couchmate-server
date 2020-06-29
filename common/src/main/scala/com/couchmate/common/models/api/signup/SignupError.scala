package com.couchmate.common.models.api.signup

import enumeratum._

sealed trait SignupError extends EnumEntry

object SignupError
  extends Enum[SignupError]
  with PlayJsonEnum[SignupError] {
  val values = findValues

  case object UsernameExists  extends SignupError
  case object EmailExists     extends SignupError
}
