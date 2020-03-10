package com.couchmate.api.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class User(
  userId: UUID,
  username: String,
) extends Product with Serializable

object User {
  implicit val format: OFormat[User] = Json.format[User]
}
