package com.couchmate.common.models.api.room.tenor

import play.api.libs.json.{Format, Json}

case class TenorSearchResult(
  weburl: String,
  results: Seq[TenorSearchItem],
  next: String
)

object TenorSearchResult {
  implicit val format: Format[TenorSearchResult] = Json.format[TenorSearchResult]
}