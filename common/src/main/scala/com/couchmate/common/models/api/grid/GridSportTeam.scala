package com.couchmate.common.models.api.grid

import play.api.libs.json.{Format, Json}

case class GridSportTeam(
  sportTeamId: Long,
  name: String,
  isHome: Boolean
)

object GridSportTeam {
  implicit val format: Format[GridSportTeam] = Json.format[GridSportTeam]
}
