package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class SportOrganization(
  sportOrganizationId: Option[Long],
  extSportId: Long,
  extOrgId: Option[Long],
  sportName: String,
  orgName: Option[String],
)

object SportOrganization extends JsonConfig {
  implicit val format: Format[SportOrganization] = Json.format[SportOrganization]
}
