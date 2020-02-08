package com.couchmate.data.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class UserPrivate(
  userId: UUID,
  password: String,
) extends Product with Serializable

object UserPrivate extends JsonConfig {
  implicit val format: OFormat[UserPrivate] = Json.format[UserPrivate]
}
