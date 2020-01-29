package com.couchmate.data.models

import java.util.UUID

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class UserMeta(
  userId: UUID,
  email: String,
) extends Product with Serializable

object UserMeta extends JsonConfig {
  implicit val format: OFormat[UserMeta] = Json.format[UserMeta]
  implicit val getResult: GetResult[UserMeta] = GenGetResult[UserMeta]
}
