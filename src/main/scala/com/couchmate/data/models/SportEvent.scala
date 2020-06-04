package com.couchmate.data.models

import play.api.libs.json.{Json, OFormat}

case class SportEvent(
  sportEventId: Option[Long],
  sportOrganizationId: Option[Long],
  sportEventTitle: String,
) extends Product with Serializable

object SportEvent extends JsonConfig {
  implicit val format: OFormat[SportEvent] = Json.format[SportEvent]
}
