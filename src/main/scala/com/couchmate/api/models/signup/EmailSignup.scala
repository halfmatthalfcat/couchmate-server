package com.couchmate.api.models.signup

import play.api.libs.json.{Json, OFormat}

case class EmailSignup(
  email: String,
  username: String,
  password: String,
  providerId: Long,
  zipCode: String,
)

object EmailSignup {
  implicit val format: OFormat[EmailSignup] = Json.format[EmailSignup]
}
