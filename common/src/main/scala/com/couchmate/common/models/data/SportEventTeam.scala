package com.couchmate.common.models.data

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class SportEventTeam(
  sportEventId: Long,
  sportOrganizationTeamId: Long,
  isHome: Boolean
)

object SportEventTeam {
  implicit val format: Format[SportEventTeam] = Json.format[SportEventTeam]
  implicit val rowParser: GetResult[SportEventTeam] = RowParser[SportEventTeam]
}