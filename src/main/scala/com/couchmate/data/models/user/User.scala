package com.couchmate.data.models.user

import java.util.UUID

import julienrf.json.derived
import play.api.libs.json._

case class User (
  user_id: Option[UUID],
  username: String,
  active: Boolean,
  verified: Boolean,
) extends Product with Serializable

object User {
  implicit val format: OFormat[User] = derived.flat.oformat[User]((__ \ "abc").format[String])
}
