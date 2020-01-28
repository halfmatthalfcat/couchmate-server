package com.couchmate.data.thirdparty.gracenote

import enumeratum._

sealed abstract class GracenoteProgramType(override val entryName: String) extends EnumEntry

object GracenoteProgramType
  extends Enum[GracenoteProgramType]
  with PlayJsonEnum[GracenoteProgramType] {

  case object Movie   extends GracenoteProgramType("Movie")
  case object Show    extends GracenoteProgramType("Show")
  case object Episode extends GracenoteProgramType("Episode")
  case object Special extends GracenoteProgramType("Special")
  case object Sports  extends GracenoteProgramType("Sports")

  val values = findValues
}
