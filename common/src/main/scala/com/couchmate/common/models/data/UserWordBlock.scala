package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserWordBlock(
  userId: UUID,
  word: String
)

object UserWordBlock {
  implicit val format: Format[UserWordBlock] = Json.format[UserWordBlock]
}
