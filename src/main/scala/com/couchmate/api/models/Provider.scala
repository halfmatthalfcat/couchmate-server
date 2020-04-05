package com.couchmate.api.models

import play.api.libs.json.{Json, OFormat}

case class Provider(
  providerId: Long,
  name: String,
  `type`: String,
  location: Option[String],
) extends Product with Serializable

object Provider {
  implicit val format: OFormat[Provider] = Json.format[Provider]
}
