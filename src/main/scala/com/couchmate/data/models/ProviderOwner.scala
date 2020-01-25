package com.couchmate.data.models

import play.api.libs.json.{Json, OFormat}

case class ProviderOwner(
  providerOwnerId: Option[Long],
  sourceId: Long,
  extProviderOwnerId: Long,
  name: String,
) extends Product with Serializable

object ProviderOwner extends JsonConfig {
  implicit val format: OFormat[ProviderOwner] = Json.format[ProviderOwner]
}
