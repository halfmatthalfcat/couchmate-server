package com.couchmate.common.models.api.room

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class Participant(
  userId: UUID,
  username: String
)

object Participant {
  implicit val format: Format[Participant] = Json.format[Participant]
}
