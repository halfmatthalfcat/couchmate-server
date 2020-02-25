package com.couchmate.data.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class UserMeta(
  userId: UUID,
  email: String,
  role: UserRole,
) extends Product with Serializable

object UserMeta extends JsonConfig {
  implicit val format: OFormat[UserMeta] = Json.format[UserMeta]
}
