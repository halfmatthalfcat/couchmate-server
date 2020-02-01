package com.couchmate.common.models

import play.api.libs.json.{Json, OFormat}

case class Provider(
  providerId: Option[Long] = None,
  providerOwnerId: Option[Long],
  extId: String,
  name: String,
  `type`: String,
  location: Option[String],
)

object Provider {
  implicit val format: OFormat[Provider] = Json.format[Provider]
}
