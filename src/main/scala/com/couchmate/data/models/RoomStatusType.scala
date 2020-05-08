package com.couchmate.data.models

import enumeratum._

sealed trait RoomStatusType extends EnumEntry

object RoomStatusType
  extends Enum[RoomStatusType]
  with PlayJsonEnum[RoomStatusType] {

  val values = findValues

  case object Closed    extends RoomStatusType
  case object Open      extends RoomStatusType
  case object PreGame   extends RoomStatusType
  case object PostGame  extends RoomStatusType
}
