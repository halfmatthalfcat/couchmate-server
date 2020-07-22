package com.couchmate.common.models.data

import enumeratum._

sealed trait UserActivityType extends EnumEntry

object UserActivityType
  extends Enum[UserActivityType]
  with PlayJsonEnum[UserActivityType] {

  val values = findValues

  case object Login       extends UserActivityType
  case object Logout      extends UserActivityType
  case object Registered  extends UserActivityType
}
