package com.couchmate.data.models

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json._

case class User (
  userId: Option[UUID],
  role: UserRole,
  active: Boolean,
  verified: Boolean,
  created: Option[LocalDateTime] = None
)

object User extends JsonConfig {
  implicit val format: OFormat[User] = Json.format[User]
}
