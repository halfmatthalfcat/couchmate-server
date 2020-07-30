package com.couchmate.common.models.api.room

import play.api.libs.json.{Format, Json}

case class Message(
  messageId: String,
  messageType: MessageType,
  message: String,
  author: Option[Participant],
  recipient: Option[Participant],
  isSelf: Boolean,
)

object Message {
  implicit val format: Format[Message] = Json.format[Message]
}