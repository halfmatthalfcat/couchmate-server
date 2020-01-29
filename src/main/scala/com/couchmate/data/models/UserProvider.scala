package com.couchmate.data.models

import java.util.UUID

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class UserProvider(
  userId: UUID,
  zipCode: String,
  providerId: Long,
) extends Product with Serializable

object UserProvider extends JsonConfig {
  implicit val format: OFormat[UserProvider] = Json.format[UserProvider]
  implicit val getResult: GetResult[UserProvider] = GenGetResult[UserProvider]
}
