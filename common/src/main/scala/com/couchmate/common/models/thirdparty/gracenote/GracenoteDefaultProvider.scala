package com.couchmate.common.models.thirdparty.gracenote

import enumeratum.values.{LongEnum, LongEnumEntry}

sealed abstract class GracenoteDefaultProvider(
  val name: String,
  val value: Long,
) extends LongEnumEntry

object GracenoteDefaultProvider extends LongEnum[GracenoteDefaultProvider] {
  val values = findValues

  case object USEast      extends GracenoteDefaultProvider(name = "USA-DFLTE", value = 1L)
  case object USCentral   extends GracenoteDefaultProvider(name = "USA-DFLTC", value = 2L)
  case object USMountain  extends GracenoteDefaultProvider(name = "USA-DFLTM", value = 3L)
  case object USPacific   extends GracenoteDefaultProvider(name = "USA-DFLTP", value = 4L)
  case object USHawaii    extends GracenoteDefaultProvider(name = "USA-DFLTH", value = 5L)
  case object USAlaska    extends GracenoteDefaultProvider(name = "USA-DFLTA", value = 6L)

  case object CANEast     extends GracenoteDefaultProvider(name = "CAN-DFLTEC", value = 7L)
  case object CANCentral  extends GracenoteDefaultProvider(name = "CAN-DFLTCC", value = 8L)
  case object CANMountain extends GracenoteDefaultProvider(name = "CAN-DFLTMC", value = 9L)
  case object CANPacific  extends GracenoteDefaultProvider(name = "CAN-DFLTPC", value = 10L)
}
