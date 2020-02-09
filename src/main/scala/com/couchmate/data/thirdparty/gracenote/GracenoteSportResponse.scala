package com.couchmate.data.thirdparty.gracenote

import play.api.libs.json._
import play.api.libs.functional.syntax._

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
