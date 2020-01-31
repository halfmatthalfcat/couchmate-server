package com.couchmate.common.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Lineup(
  lineupId: Option[Long],
  providerChannelId: Long,
  airingId: UUID,
  replacedBy: Option[UUID]
) extends Product with Serializable

object Lineup extends JsonConfig {
  implicit val format: OFormat[Lineup] = Json.format[Lineup]
}
