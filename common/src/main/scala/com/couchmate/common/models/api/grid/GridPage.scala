package com.couchmate.common.models.api.grid

import java.time.LocalDateTime

import play.api.libs.json.{Format, Json}

case class GridPage(
  slot: LocalDateTime,
  channels: List[GridChannel]
)

object GridPage {
  implicit val format: Format[GridPage] = Json.format[GridPage]
}