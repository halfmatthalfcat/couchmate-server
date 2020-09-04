package com.couchmate.common.models.data

import enumeratum._

sealed trait UserReportType extends EnumEntry

object UserReportType
  extends Enum[UserReportType]
  with PlayJsonEnum[UserReportType] {
  val values = findValues

  case object Harassment      extends UserReportType
  case object HateSpeech      extends UserReportType
  case object Spamming        extends UserReportType
  case object ChildSafety     extends UserReportType
  case object Doxxing         extends UserReportType
  case object Extremism       extends UserReportType
  case object Impersonation   extends UserReportType
  case object IllegalActivity extends UserReportType
  case object DCMA            extends UserReportType
}
