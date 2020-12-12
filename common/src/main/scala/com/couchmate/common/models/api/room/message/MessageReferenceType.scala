package com.couchmate.common.models.api.room.message

import enumeratum._

sealed trait MessageReferenceType extends EnumEntry

object MessageReferenceType
  extends Enum[MessageReferenceType]
  with PlayJsonEnum[MessageReferenceType] {
  val values = findValues

  case object User extends MessageReferenceType
  case object HashRoom extends MessageReferenceType
  case object Link extends MessageReferenceType
}
