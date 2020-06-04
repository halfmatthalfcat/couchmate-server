package com.couchmate.external.gracenote.models

import java.time.LocalDateTime

case class GracenoteAiringPlan(
  providerChannelId: Long,
  startTime: LocalDateTime,
  add: Seq[GracenoteAiring],
  remove: Seq[GracenoteAiring],
  skip: Seq[GracenoteAiring]
)
