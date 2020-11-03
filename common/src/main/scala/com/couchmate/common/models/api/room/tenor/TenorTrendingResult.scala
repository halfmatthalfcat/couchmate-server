package com.couchmate.common.models.api.room.tenor

import play.api.libs.json.{Format, Json}

case class TenorTrendingResult(
  results: Seq[String]
)

object TenorTrendingResult {
  implicit val format: Format[TenorTrendingResult] = Json.format[TenorTrendingResult]
}
