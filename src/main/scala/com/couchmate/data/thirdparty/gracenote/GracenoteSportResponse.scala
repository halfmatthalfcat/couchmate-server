package com.couchmate.data.thirdparty.gracenote

import play.api.libs.json.{Json, OFormat}

case class GracenoteSportResponse(
  sportId: Long,
  sportsName: String,
  organizations: Seq[GracenoteSportOrganization],
)

object GracenoteSportResponse {
  implicit val format: OFormat[GracenoteSportResponse] = Json.format[GracenoteSportResponse]
}
