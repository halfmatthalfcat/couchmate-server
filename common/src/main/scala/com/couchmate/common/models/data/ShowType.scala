package com.couchmate.common.models.data

import enumeratum._

sealed trait ShowType extends EnumEntry

object ShowType
  extends Enum[ShowType]
  with PlayJsonEnum[ShowType] {
  val values = findValues

  case object Show    extends ShowType
  case object Episode extends ShowType
  case object Sport   extends ShowType
  case object Movie   extends ShowType
}
