package com.couchmate.common.models.api.room

import java.util.UUID

import com.couchmate.common.models.api.user.UserFlair
import play.api.libs.json.{Format, Json}

case class Participant(
  userId: UUID,
  username: String,
  flair: List[UserFlair]
)

object Participant {
  implicit val format: Format[Participant] = Json.format[Participant]
}
