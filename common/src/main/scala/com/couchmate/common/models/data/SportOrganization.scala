package com.couchmate.common.models.data

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class SportOrganization(
  sportOrganizationId: Option[Long],
  extSportId: Long,
  extOrgId: Option[Long] = Option(0L),
  sportName: String,
  orgName: Option[String],
)

object SportOrganization extends JsonConfig {
  implicit val format: Format[SportOrganization] = Json.format[SportOrganization]
  implicit val rowParser: GetResult[SportOrganization] = RowParser[SportOrganization]
}
