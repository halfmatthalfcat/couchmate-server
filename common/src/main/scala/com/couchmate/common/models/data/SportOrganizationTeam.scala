package com.couchmate.common.models.data

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class SportOrganizationTeam(
  sportOrganizationTeamId: Option[Long],
  sportTeamId: Long,
  sportOrganizationId: Long
)

object SportOrganizationTeam {
  implicit val format: Format[SportOrganizationTeam] = Json.format[SportOrganizationTeam]
  implicit val rowParser: GetResult[SportOrganizationTeam] = RowParser[SportOrganizationTeam]
}
