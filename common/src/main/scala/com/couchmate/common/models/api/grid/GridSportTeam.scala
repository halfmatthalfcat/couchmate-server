package com.couchmate.common.models.api.grid

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class GridSportTeam(
  sportTeamId: Long,
  name: String,
  isHome: Boolean,
  following: Long
)

object GridSportTeam {
  implicit val format: Format[GridSportTeam] = Json.format[GridSportTeam]
  implicit val rowParser: GetResult[GridSportTeam] = RowParser[GridSportTeam]
}
