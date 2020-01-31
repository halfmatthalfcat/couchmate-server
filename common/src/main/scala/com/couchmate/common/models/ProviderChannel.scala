package com.couchmate.common.models

import play.api.libs.json.{Json, OFormat}

case class ProviderChannel(
  providerChannelId: Option[Long],
  providerId: Long,
  channelId: Long,
  channel: String,
) extends Product with Serializable

object ProviderChannel extends JsonConfig {
  implicit val format: OFormat[ProviderChannel] = Json.format[ProviderChannel]
}
