package com.couchmate.common.models.api.user

import play.api.libs.json.{Format, Json}

case class UserFlair(
  color: String,
  text: String
)

object UserFlair {
  implicit val format: Format[UserFlair] = Json.format[UserFlair]
}
