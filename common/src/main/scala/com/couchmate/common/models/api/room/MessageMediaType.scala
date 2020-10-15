package com.couchmate.common.models.api.room

import enumeratum._

sealed trait MessageMediaType extends EnumEntry

object MessageMediaType
  extends Enum[MessageMediaType]
  with PlayJsonEnum[MessageMediaType] {
  val values = findValues

  case object Link  extends MessageMediaType
  case object Tenor extends MessageMediaType
  case object Emoji extends MessageMediaType
}