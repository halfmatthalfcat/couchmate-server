package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserPrivate(
  userId: UUID,
  password: String,
)

object UserPrivate extends JsonConfig {
  implicit val format: Format[UserPrivate] = Json.format[UserPrivate]
}
