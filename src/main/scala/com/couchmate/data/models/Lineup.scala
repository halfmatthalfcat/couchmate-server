package com.couchmate.data.models

import java.util.UUID

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class Lineup(
  lineupId: Option[Long],
  providerChannelId: Long,
  airingId: UUID,
  replacedBy: Option[UUID]
) extends Product with Serializable

object Lineup extends JsonConfig {
  implicit val format: OFormat[Lineup] = Json.format[Lineup]
  implicit val getResult: GetResult[Lineup] = GenGetResult[Lineup]
}
