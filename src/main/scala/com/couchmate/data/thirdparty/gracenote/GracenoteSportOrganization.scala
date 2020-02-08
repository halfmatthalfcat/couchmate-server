package com.couchmate.data.thirdparty.gracenote

import play.api.libs.json.{Json, OFormat}

case class GracenoteSportOrganization(
  organizationId: Long,
  organizationName: String,
  officialOrg: Boolean,
)

object GracenoteSportOrganization {
  implicit val format: OFormat[GracenoteSportOrganization] = Json.format[GracenoteSportOrganization]
}
