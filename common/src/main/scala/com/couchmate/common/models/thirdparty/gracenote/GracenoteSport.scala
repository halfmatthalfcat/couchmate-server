package com.couchmate.common.models.thirdparty.gracenote

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class GracenoteSport(
  sportsId: Long,
  sportsName: String,
  organizations: Seq[GracenoteSportOrganization],
)

object GracenoteSport {
  implicit val reads: Reads[GracenoteSport] = (
    (__ \ "sportsId").read[String].map(_.toLong) and
    (__ \ "sportsName").read[String] and
    (__ \ "organizations").readWithDefault[Seq[GracenoteSportOrganization]](Seq[GracenoteSportOrganization]())
  )(GracenoteSport.apply _)
}
