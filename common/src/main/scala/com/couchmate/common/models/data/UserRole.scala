package com.couchmate.common.models.data

import enumeratum._

sealed trait UserRole extends EnumEntry

object UserRole
  extends Enum[UserRole]
  with PlayJsonEnum[UserRole] {

  val values = findValues

  case object Admin       extends UserRole
  case object Anon        extends UserRole
  case object Registered  extends UserRole
  case object Subbed      extends UserRole
  case object Moderator   extends UserRole
  case object Industry    extends UserRole

}
