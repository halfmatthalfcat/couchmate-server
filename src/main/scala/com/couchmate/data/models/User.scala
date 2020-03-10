package com.couchmate.data.models

import java.util.UUID

import play.api.libs.json._

case class User (
  userId: Option[UUID],
  username: String,
  role: UserRole,
  active: Boolean,
  verified: Boolean,
) extends Product with Serializable

object User extends JsonConfig {
  implicit val format: OFormat[User] = Json.format[User]
}
