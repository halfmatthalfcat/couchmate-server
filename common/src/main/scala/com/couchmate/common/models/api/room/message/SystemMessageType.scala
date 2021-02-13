package com.couchmate.common.models.api.room.message

import enumeratum._

sealed trait SystemMessageType extends EnumEntry

object SystemMessageType
  extends Enum[SystemMessageType]
  with PlayJsonEnum[SystemMessageType] {
  val values = findValues

  case object ShowEnd extends SystemMessageType
}