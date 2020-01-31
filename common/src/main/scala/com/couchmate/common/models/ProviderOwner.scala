package com.couchmate.common.models

import play.api.libs.json.{Json, OFormat}

case class ProviderOwner(
  providerOwnerId: Option[Long] = None,
  extProviderOwnerId: Option[String] = None,
  name: String,
) extends Product with Serializable

object ProviderOwner extends JsonConfig {
  implicit val format: OFormat[ProviderOwner] = Json.format[ProviderOwner]
}
