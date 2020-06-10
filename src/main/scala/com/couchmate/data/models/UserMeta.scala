package com.couchmate.data.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class UserMeta(
  userId: UUID,
  username: String,
  email: Option[String],
) extends Product with Serializable

object UserMeta extends JsonConfig {
  implicit val format: OFormat[UserMeta] = Json.format[UserMeta]
}
