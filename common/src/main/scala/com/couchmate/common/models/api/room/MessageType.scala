package com.couchmate.common.models.api.room

import enumeratum._

sealed trait MessageType extends EnumEntry

object MessageType
  extends Enum[MessageType]
  with PlayJsonEnum[MessageType] {
  val values = findValues

  case object Room    extends MessageType
  case object DM      extends MessageType
  case object System  extends MessageType
  case object Admin   extends MessageType
  case object Mod     extends MessageType
}