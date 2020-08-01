package com.couchmate.common.models.api.user

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserMute(
  userId: UUID,
  username: String
)

object UserMute {
  implicit val format: Format[UserMute] = Json.format[UserMute]
}
