package com.couchmate.common.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class UserMeta(
  userId: UUID,
  email: String,
) extends Product with Serializable

object UserMeta extends JsonConfig {
  implicit val format: OFormat[UserMeta] = Json.format[UserMeta]
}
