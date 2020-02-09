package com.couchmate.data.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Lineup(
  lineupId: Option[Long],
  providerChannelId: Long,
  airingId: UUID,
  active: Boolean
) extends Product with Serializable

object Lineup extends JsonConfig {
  implicit val format: OFormat[Lineup] = Json.format[Lineup]
}
