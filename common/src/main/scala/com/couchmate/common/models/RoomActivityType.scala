package com.couchmate.common.models

import enumeratum._

sealed trait RoomActivityType extends EnumEntry

object RoomActivityType
  extends Enum[RoomActivityType]
  with PlayJsonEnum[RoomActivityType]
  with QuillEnum[RoomActivityType] {

  val values = findValues

  case object Joined  extends RoomActivityType
  case object Left    extends RoomActivityType
  case object Kicked  extends RoomActivityType
}
