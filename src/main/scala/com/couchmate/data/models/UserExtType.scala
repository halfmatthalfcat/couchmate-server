package com.couchmate.data.models

import enumeratum._

sealed trait UserExtType extends EnumEntry

object UserExtType
  extends Enum[UserExtType]
  with PlayJsonEnum[UserExtType] {

  val values = findValues

  case object Facebook  extends EnumEntry
  case object Google    extends EnumEntry

}
