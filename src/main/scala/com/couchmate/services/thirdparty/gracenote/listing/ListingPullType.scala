package com.couchmate.services.thirdparty.gracenote.listing

import enumeratum.values.{IntEnum, IntEnumEntry}

sealed abstract class ListingPullType(val value: Int) extends IntEnumEntry

object ListingPullType
  extends IntEnum[ListingPullType] {

  case object Initial extends ListingPullType(1)
  case object Full    extends ListingPullType(168)

  val values = findValues
}
