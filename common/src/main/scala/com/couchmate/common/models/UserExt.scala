package com.couchmate.common.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class UserExt(
  userId: UUID,
  extType: UserExtType,
  extId: String,
) extends Product with Serializable

object UserExt extends JsonConfig {
  implicit val format: OFormat[UserExt] = Json.format[UserExt]
}
