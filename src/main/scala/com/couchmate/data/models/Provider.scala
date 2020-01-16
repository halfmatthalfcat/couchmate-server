package com.couchmate.data.models

import play.api.libs.json.{Json, OFormat}

case class Provider(
  providerId: Option[Long],
  sourceId: Long,
  extId: String,
  name: String,
  `type`: Option[String],
  location: Option[String],
) extends Product with Serializable

object Provider extends JsonConfig {
  implicit val format: OFormat[Provider] = Json.format[Provider]
}
