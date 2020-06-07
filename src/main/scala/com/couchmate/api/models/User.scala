package com.couchmate.api.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class User(
  userId: UUID,
  username: String,
  token: String,
)

object User {
  implicit val format: OFormat[User] = Json.format[User]
}
