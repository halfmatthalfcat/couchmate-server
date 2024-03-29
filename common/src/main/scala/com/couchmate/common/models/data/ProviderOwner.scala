package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class ProviderOwner(
  providerOwnerId: Option[Long] = None,
  extProviderOwnerId: String,
  name: String,
)

object ProviderOwner extends JsonConfig {
  implicit val format: Format[ProviderOwner] = Json.format[ProviderOwner]
}
