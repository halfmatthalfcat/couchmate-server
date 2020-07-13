package com.couchmate.common.models.data

import java.time.LocalDateTime

case class ListingJob(
  listingJobId: Option[Long] = None,
  providerId: Long,
  pullAmount: Int,
  started: LocalDateTime,
  completed: Option[LocalDateTime] = None,
  baseSlot: LocalDateTime,
  lastSlot: Option[LocalDateTime] = None,
  status: ListingJobStatus
)
