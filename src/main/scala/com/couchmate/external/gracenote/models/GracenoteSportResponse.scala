package com.couchmate.external.gracenote.models

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class GracenoteSportResponse(
  sportsId: Long,
  sportsName: String,
  organizations: Seq[GracenoteSportOrganization],
)

object GracenoteSportResponse {
  implicit val reads: Reads[GracenoteSportResponse] = (
    (__ \ "sportsId").read[String].map(_.toLong) and
    (__ \ "sportsName").read[String] and
    (__ \ "organizations").readWithDefault[Seq[GracenoteSportOrganization]](Seq[GracenoteSportOrganization]())
  )(GracenoteSportResponse.apply _)
}
