package com.couchmate.data.models

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class SportOrganization(
  sportOrganizationId: Option[Long],
  extSportId: Long,
  extOrgId: Option[Int],
  sportName: String,
  orgName: Option[String],
) extends Product with Serializable

object SportOrganization extends JsonConfig {
  implicit val format: OFormat[SportOrganization] = Json.format[SportOrganization]
  implicit val getResult: GetResult[SportOrganization] = GenGetResult[SportOrganization]
}
