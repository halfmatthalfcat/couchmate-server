package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserExt(
  userId: UUID,
  extType: UserExtType,
  extId: String,
)

object UserExt extends JsonConfig {
  implicit val format: Format[UserExt] = Json.format[UserExt]
}
