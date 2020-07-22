package com.couchmate.common.models.api.user

import java.util.UUID

import com.couchmate.common.models.data.UserRole
import play.api.libs.json.{Json, OFormat}

case class User(
  userId: UUID,
  verified: Boolean,
  role: UserRole,
  username: String,
  email: Option[String],
  token: String,
  mutes: Seq[UUID],
)

object User {
  implicit val format: OFormat[User] = Json.format[User]
}
