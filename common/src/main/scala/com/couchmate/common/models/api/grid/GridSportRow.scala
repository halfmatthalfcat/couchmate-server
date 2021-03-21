package com.couchmate.common.models.api.grid

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class GridSportRow(
  sportEventId: Long,
  sportTeamId: Long,
  eventName: String,
  sportName: Option[String],
  orgName: Option[String],
  name: String,
  isHome: Boolean,
  following: Long
) {
  def toGridSport: GridSport = GridSport(
    sportEventId = sportEventId,
    sportTitle = eventName,
    sportName = sportName.getOrElse("Unknown"),
    sportOrganization = orgName,
    teams = Seq.empty
  )

  def toGridSportTeam: GridSportTeam = GridSportTeam(
    sportTeamId = sportTeamId,
    name = name,
    isHome = isHome,
    following = following
  )
}

object GridSportRow {
  implicit val format: Format[GridSportRow] = Json.format[GridSportRow]
  implicit val rowParser: GetResult[GridSportRow] = RowParser[GridSportRow]
}
