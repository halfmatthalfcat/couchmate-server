package com.couchmate.common.models

import play.api.libs.json.{Json, OFormat}

case class Provider(
  providerId: Option[Long] = None,
  providerOwnerId: Option[Long],
  extId: String,
  name: String,
  `type`: String,
  location: Option[String],
) extends Product with Serializable

object Provider extends JsonConfig {
  implicit val format: OFormat[Provider] = Json.format[Provider]
}
