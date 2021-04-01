package com.couchmate.common.models.api.grid

import play.api.libs.json.{Format, Json}

case class GridDynamic(
  providerId: Long,
  userCount: Long,
  airings: Seq[GridAiringDynamic]
)

object GridDynamic {
  implicit val format: Format[GridDynamic] = Json.format[GridDynamic]
}
