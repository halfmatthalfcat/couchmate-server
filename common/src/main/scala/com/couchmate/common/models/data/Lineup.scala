package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class Lineup(
  lineupId: Option[Long],
  providerChannelId: Long,
  airingId: UUID,
  active: Boolean
) extends Product with Serializable

object Lineup extends JsonConfig {
  implicit val format: Format[Lineup] = Json.format[Lineup]
}
