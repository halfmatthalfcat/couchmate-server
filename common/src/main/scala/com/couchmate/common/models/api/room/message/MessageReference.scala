package com.couchmate.common.models.api.room.message

import play.api.libs.json.{Format, Json}

case class MessageReference(
  id: String,
  value: String,
  startIdx: Int,
  endIdx: Int,
  messageReferenceType: MessageReferenceType,
)

object MessageReference {
  implicit val format: Format[MessageReference] = Json.format[MessageReference]
}
