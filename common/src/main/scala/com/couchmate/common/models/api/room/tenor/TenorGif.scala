package com.couchmate.common.models.api.room.tenor

import play.api.libs.json.{Format, Json}

case class TenorGif(
  url: String,
  title: String,
  height: Int,
  width: Int
)

object TenorGif {
  implicit val format: Format[TenorGif] = Json.format[TenorGif]
}
