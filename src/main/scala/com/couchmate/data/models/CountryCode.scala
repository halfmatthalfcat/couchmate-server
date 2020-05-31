package com.couchmate.data.models

import enumeratum._

sealed abstract class CountryCode(override val entryName: String) extends EnumEntry

// ISO 3166-1 alpha-3 country codes
object CountryCode
  extends Enum[CountryCode]
  with PlayJsonEnum[CountryCode] {
  val values = findValues

  case object USA         extends CountryCode("USA")
  case object Canada      extends CountryCode("CAN")

  case object Austria     extends CountryCode("AUS")
  case object Switzerland extends CountryCode("CHE")
  case object Germany     extends CountryCode("DEU")
  case object Denmark     extends CountryCode("DNK")
  case object Spain       extends CountryCode("ESP")
  case object Finland     extends CountryCode("FIN")
  case object France      extends CountryCode("FRA")
  case object UK          extends CountryCode("GBR")
  case object Italy       extends CountryCode("ITA")
  case object Norway      extends CountryCode("NOR")
  case object Sweden      extends CountryCode("SWE")
}
