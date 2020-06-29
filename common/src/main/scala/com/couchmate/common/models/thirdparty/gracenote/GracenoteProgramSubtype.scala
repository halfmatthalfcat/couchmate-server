package com.couchmate.common.models.thirdparty.gracenote

import enumeratum._

sealed abstract class GracenoteProgramSubtype(override val entryName: String) extends EnumEntry

object GracenoteProgramSubtype
  extends Enum[GracenoteProgramSubtype]
  with PlayJsonEnum[GracenoteProgramSubtype] {

  case object FeatureFilm     extends GracenoteProgramSubtype("Feature Film")
  case object ShortFilm       extends GracenoteProgramSubtype("Short Film")
  case object TvMovie         extends GracenoteProgramSubtype("TV Movie")
  case object Miniseries      extends GracenoteProgramSubtype("Miniseries")
  case object Series          extends GracenoteProgramSubtype("Series")
  case object Special         extends GracenoteProgramSubtype("Special")
  case object SportsEvent     extends GracenoteProgramSubtype("Sports event")
  case object SportsNonEvent  extends GracenoteProgramSubtype("Sports non-event")
  case object PaidProgramming extends GracenoteProgramSubtype("Paid Programming")
  case object TheatreEvent    extends GracenoteProgramSubtype("Theatre Event")
  case object Unknown         extends GracenoteProgramSubtype("Unknown")

  val values = findValues
}
