package com.couchmate.data.models.user

import enumeratum._

sealed trait UserType extends EnumEntry

object UserType extends Enum[UserType] with PlayJsonEnum[UserType] {

  val values: IndexedSeq[UserType] = findValues

  case object Admin extends UserType
  case object Anon extends UserType
  case object Registered extends UserType
  case object Subbed extends UserType
  case object Analytics extends UserType

}
