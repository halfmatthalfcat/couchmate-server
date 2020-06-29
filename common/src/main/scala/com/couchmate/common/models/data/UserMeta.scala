package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserMeta(
  userId: UUID,
  username: String,
  email: Option[String],
)

object UserMeta extends JsonConfig {
  implicit val format: Format[UserMeta] = Json.format[UserMeta]
}
