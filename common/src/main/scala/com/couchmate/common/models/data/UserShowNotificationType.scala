package com.couchmate.common.models.data

import enumeratum._

sealed trait UserShowNotificationType extends EnumEntry

object UserShowNotificationType
  extends Enum[UserShowNotificationType]
  with PlayJsonEnum[UserShowNotificationType] {
  val values = findValues

  case object Show    extends UserShowNotificationType
  case object Series  extends UserShowNotificationType
  case object Team    extends UserShowNotificationType
}
