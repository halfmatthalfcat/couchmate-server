package com.couchmate.common.models.thirdparty.gracenote

import play.api.libs.json.{Format, Json}

case class GracenoteSportTeam(
  teamBrandId: String,
  name: String,
  isHome: Option[Boolean]
)

object GracenoteSportTeam {
  implicit val format: Format[GracenoteSportTeam] = Json.format[GracenoteSportTeam]
}
