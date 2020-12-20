package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class SportEventTeam(
  sportEventId: Long,
  sportTeamId: Long,
  isHome: Boolean
)

object SportEventTeam {
  implicit val format: Format[SportEventTeam] = Json.format[SportEventTeam]
}