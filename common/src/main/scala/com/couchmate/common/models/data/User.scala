package com.couchmate.common.models.data

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class User (
  userId: Option[UUID],
  role: UserRole,
  active: Boolean,
  verified: Boolean,
  created: Option[LocalDateTime] = None
)

object User extends JsonConfig {
  implicit val format: Format[User] = Json.format[User]
}
