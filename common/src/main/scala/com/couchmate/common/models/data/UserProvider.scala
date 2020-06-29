package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserProvider(
  userId: UUID,
  providerId: Long,
)

object UserProvider extends JsonConfig {
  implicit val format: Format[UserProvider] = Json.format[UserProvider]
}
