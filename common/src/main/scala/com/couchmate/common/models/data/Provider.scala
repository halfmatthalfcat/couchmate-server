package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class Provider(
  providerId: Option[Long] = None,
  providerOwnerId: Option[Long],
  extId: String,
  name: String,
  `type`: String,
  location: Option[String],
)

object Provider {
  implicit val format: Format[Provider] = Json.format[Provider]
}
