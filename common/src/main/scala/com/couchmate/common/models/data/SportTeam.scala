package com.couchmate.common.models.data

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class SportTeam(
  sportTeamId: Option[Long],
  extSportTeamId: Long,
  name: String
)

object SportTeam {
  implicit val format: Format[SportTeam] = Json.format[SportTeam]
  implicit val rowParser: GetResult[SportTeam] = RowParser[SportTeam]
}
