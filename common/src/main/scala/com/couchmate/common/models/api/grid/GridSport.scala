package com.couchmate.common.models.api.grid

import play.api.libs.json.{Format, Json}

case class GridSport(
  sportEventId: Long,
  sportTitle: String,
  sportName: String,
  sportOrganization: Option[String],
  teams: Seq[GridSportTeam]
)

object GridSport {
  implicit val format: Format[GridSport] = Json.format[GridSport]
}