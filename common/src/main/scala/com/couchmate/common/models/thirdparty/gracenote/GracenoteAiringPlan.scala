package com.couchmate.common.models.thirdparty.gracenote

import java.time.LocalDateTime

case class GracenoteAiringPlan(
  providerChannelId: Long,
  startTime: LocalDateTime,
  add: Seq[GracenoteAiring],
  remove: Seq[GracenoteAiring],
  skip: Seq[GracenoteAiring]
)
