package com.couchmate.data.models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}

case class Lineup(
  providerChannelId: Long,
  airingId: UUID,
  replacedBy: Option[UUID]
) extends Product with Serializable

object Lineup extends JsonConfig {
  implicit val format: OFormat[Lineup] = Json.format[Lineup]
}
