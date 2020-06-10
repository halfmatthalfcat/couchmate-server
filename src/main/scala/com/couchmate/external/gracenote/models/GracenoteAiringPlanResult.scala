package com.couchmate.external.gracenote.models

import java.time.LocalDateTime

case class GracenoteAiringPlanResult (
  startTime: LocalDateTime,
  added: Int,
  removed: Int,
  skipped: Int
)
