package com.couchmate.common.models

import play.api.libs.json.{Json, OFormat}

case class SportOrganization(
  sportOrganizationId: Option[Long],
  extSportId: Long,
  extOrgId: Option[Int],
  sportName: String,
  orgName: Option[String],
) extends Product with Serializable

object SportOrganization extends JsonConfig {
  implicit val format: OFormat[SportOrganization] = Json.format[SportOrganization]
}
