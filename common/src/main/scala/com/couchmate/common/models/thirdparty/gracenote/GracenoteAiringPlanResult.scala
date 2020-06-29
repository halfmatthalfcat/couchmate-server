package com.couchmate.common.models.thirdparty.gracenote

import java.time.LocalDateTime

case class GracenoteAiringPlanResult (
  startTime: LocalDateTime,
  added: Int,
  removed: Int,
  skipped: Int
)
