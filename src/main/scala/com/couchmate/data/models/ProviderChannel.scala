package com.couchmate.data.models

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class ProviderChannel(
  providerChannelId: Option[Long],
  providerId: Long,
  channelId: Long,
  channel: String,
) extends Product with Serializable

object ProviderChannel extends JsonConfig {
  implicit val format: OFormat[ProviderChannel] = Json.format[ProviderChannel]
  implicit val getResult: GetResult[ProviderChannel] = GenGetResult[ProviderChannel]
}
