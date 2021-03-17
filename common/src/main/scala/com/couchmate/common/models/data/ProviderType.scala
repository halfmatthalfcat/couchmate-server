package com.couchmate.common.models.data

import enumeratum._

sealed abstract class ProviderType(override val entryName: String) extends EnumEntry

object ProviderType
  extends Enum[ProviderType]
  with PlayJsonEnum[ProviderType] {
  val values = findValues

  case object Default   extends ProviderType("Default")
  case object Cable     extends ProviderType("CABLE")
  case object Digital   extends ProviderType("VMVPD")
  case object OTA       extends ProviderType("OTA")
  case object Satellite extends ProviderType("SATELLITE")
  case object Unknown   extends ProviderType("Unknown")
}
