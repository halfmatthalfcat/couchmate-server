package com.couchmate.api.models.grid

import java.time.LocalDateTime

case class GridAiring(
  airingId: Long,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  title: String,
  description: String,
  `type`:
)
