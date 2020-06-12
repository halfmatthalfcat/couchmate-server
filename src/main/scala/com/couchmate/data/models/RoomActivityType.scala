package com.couchmate.data.models

import enumeratum._

sealed trait RoomActivityType extends EnumEntry

object RoomActivityType
  extends Enum[RoomActivityType]
  with PlayJsonEnum[RoomActivityType] {

  val values = findValues

  case object Joined  extends RoomActivityType
  case object Left    extends RoomActivityType

  object Kicked {
    case object Inactive  extends RoomActivityType
    case object Expired   extends RoomActivityType
    case object Voted     extends RoomActivityType
    case object Forced    extends RoomActivityType
  }
}
