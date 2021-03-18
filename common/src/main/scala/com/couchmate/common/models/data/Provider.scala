package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class Provider(
  providerId: Option[Long] = None,
  providerOwnerId: Long,
  extId: String,
  name: String,
  `type`: ProviderType,
  location: Option[String],
  device: Option[String]
)

object Provider {
  implicit val format: Format[Provider] = Json.format[Provider]
}
