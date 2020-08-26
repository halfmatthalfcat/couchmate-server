package com.couchmate.common.models.api.grid

import java.time.LocalDateTime

import play.api.libs.json.{Format, Json}

case class AiringConversion(
  provider: String,
  extChannelId: Int,
  extShowId: Int,
  startTime: LocalDateTime
)

object AiringConversion {
  implicit val format: Format[AiringConversion] = Json.format[AiringConversion]
}
