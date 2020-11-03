package com.couchmate.common.models.api.room.tenor

import play.api.libs.json.{Format, Json}

case class TenorMediaItem(
  url: String,
  dims: Seq[Int],
  size: Option[Int]
)

object TenorMediaItem {
  implicit val format: Format[TenorMediaItem] = Json.format[TenorMediaItem]
}