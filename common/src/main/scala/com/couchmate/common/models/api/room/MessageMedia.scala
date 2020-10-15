package com.couchmate.common.models.api.room

import play.api.libs.json.{Format, Json}

case class MessageMedia(
  url: String,
  text: String,
  height: Int,
  width: Int,
  messageMediaType: MessageMediaType
)

object MessageMedia {
  implicit val format: Format[MessageMedia] = Json.format[MessageMedia]
}
