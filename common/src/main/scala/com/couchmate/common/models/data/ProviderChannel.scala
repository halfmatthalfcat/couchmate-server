package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class ProviderChannel(
  providerChannelId: Option[Long],
  providerId: Long,
  channelId: Long,
  channel: String,
) extends Product with Serializable

object ProviderChannel extends JsonConfig {
  implicit val format: Format[ProviderChannel] = Json.format[ProviderChannel]
}
