package com.couchmate.common.models.api.room.tenor

import play.api.libs.json.{Format, Json}

case class TenorSearchItem(
  url: String,
  tags: Seq[String],
  title: String,
  id: String,
  media: Seq[TenorMediaItems]
)

object TenorSearchItem {
  implicit val format: Format[TenorSearchItem] = Json.format[TenorSearchItem]
}
