package com.couchmate.common.models.thirdparty.gracenote

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class GracenoteSportOrganization(
  organizationId: Long,
  organizationName: Option[String],
  officialOrg: Boolean,
)

object GracenoteSportOrganization {
  implicit val reads: Reads[GracenoteSportOrganization] = (
    (__ \ "organizationId").read[String].map(_.toLong) and
    (__ \ "organizationName").readNullable[String] and
    (__ \ "officialOrg").read[Boolean]
  )(GracenoteSportOrganization.apply _)
}
