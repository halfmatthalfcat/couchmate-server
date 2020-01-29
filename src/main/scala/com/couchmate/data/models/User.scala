package com.couchmate.data.models

import java.util.UUID

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json._
import slick.jdbc.GetResult

case class User (
  userId: Option[UUID],
  username: String,
  active: Boolean,
  verified: Boolean,
) extends Product with Serializable

object User extends JsonConfig {
  implicit val format: OFormat[User] = Json.format[User]
  implicit val getResult: GetResult[User] = GenGetResult[User]
}
