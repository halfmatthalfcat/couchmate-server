package com.couchmate.common.models

import enumeratum._

sealed trait UserActivityType extends EnumEntry

object UserActivityType
  extends Enum[UserActivityType]
  with PlayJsonEnum[UserActivityType]
  with QuillEnum[UserActivityType] {

  val values = findValues

  case object Login   extends UserActivityType
  case object Logout  extends UserActivityType

}
