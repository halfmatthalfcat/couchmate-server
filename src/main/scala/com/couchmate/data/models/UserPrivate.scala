package com.couchmate.data.models

import java.util.UUID

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class UserPrivate(
  userId: UUID,
  password: String,
) extends Product with Serializable

object UserPrivate extends JsonConfig {
  implicit val format: OFormat[UserPrivate] = Json.format[UserPrivate]
  implicit val getResult: GetResult[UserPrivate] = GenGetResult[UserPrivate]
}
