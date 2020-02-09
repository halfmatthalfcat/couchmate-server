package com.couchmate.data.thirdparty.gracenote

import play.api.libs.json._
import play.api.libs.functional.syntax._

case class GracenoteSportOrganization(
  organizationId: Long,
  organizationName: String,
  officialOrg: Boolean,
)

object GracenoteSportOrganization {
  implicit val reads: Reads[GracenoteSportOrganization] = (
    (__ \ "organizationId").read[String].map(_.toLong) and
    (__ \ "organizationName").read[String] and
    (__ \ "officialOrg").read[Boolean]
  )(GracenoteSportOrganization.apply _)
}
