package com.couchmate.common.models.api.signup

import play.api.libs.json.{Json, OFormat}

case class AnonSignup(
  zipCode: String,
  providerId: Long
) extends Product with Serializable

object AnonSignup {
  implicit val format: OFormat[AnonSignup] = Json.format[AnonSignup]
}
