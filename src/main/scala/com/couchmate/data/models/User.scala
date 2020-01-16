package com.couchmate.data.models

import java.util.UUID

import julienrf.json.derived
import play.api.libs.json._

case class User (
  userId: Option[UUID],
  username: String,
  active: Boolean,
  verified: Boolean,
) extends Product with Serializable

object User extends JsonConfig {
  implicit val format: OFormat[User] = derived.flat.oformat[User]((__ \ "abc").format[String])
}
