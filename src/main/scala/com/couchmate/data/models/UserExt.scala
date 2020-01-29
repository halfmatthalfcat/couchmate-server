package com.couchmate.data.models

import java.util.UUID

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class UserExt(
  userId: UUID,
  extType: UserExtType,
  extId: String,
) extends Product with Serializable

object UserExt extends JsonConfig {
  implicit val format: OFormat[UserExt] = Json.format[UserExt]
  implicit val getResult: GetResult[UserExt] = GenGetResult[UserExt]
}
