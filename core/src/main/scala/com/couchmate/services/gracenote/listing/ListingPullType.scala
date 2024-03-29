package com.couchmate.services.gracenote.listing

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class ListingPullType(val value: Int) extends IntEnumEntry

object ListingPullType
  extends IntEnum[ListingPullType] {
  val values = findValues

  case object SixHours  extends ListingPullType(6)
  case object HalfDay   extends ListingPullType(12)
  case object Day       extends ListingPullType(36)
  case object Week      extends ListingPullType(168)
  case object TwoWeeks  extends ListingPullType(336)
}
