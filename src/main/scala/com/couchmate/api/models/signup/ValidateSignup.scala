package com.couchmate.api.models.signup

import play.api.libs.json.{Format, Json}

case class ValidateSignup(
  username: Boolean,
  email: Boolean
)

object ValidateSignup {
  implicit val format: Format[ValidateSignup] = Json.format[ValidateSignup]
}
