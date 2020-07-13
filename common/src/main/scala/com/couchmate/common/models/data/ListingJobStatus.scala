package com.couchmate.common.models.data

import enumeratum._

sealed trait ListingJobStatus extends EnumEntry

object ListingJobStatus extends Enum[ListingJobStatus] {
  val values = findValues

  case object Complete    extends ListingJobStatus
  case object InProgress  extends ListingJobStatus
  case object Error       extends ListingJobStatus
}
