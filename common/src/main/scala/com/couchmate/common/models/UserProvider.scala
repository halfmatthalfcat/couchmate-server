package com.couchmate.common.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class UserProvider(
  userId: UUID,
  zipCode: String,
  providerId: Long,
) extends Product with Serializable

object UserProvider extends JsonConfig {
  implicit val format: OFormat[UserProvider] = Json.format[UserProvider]
}
