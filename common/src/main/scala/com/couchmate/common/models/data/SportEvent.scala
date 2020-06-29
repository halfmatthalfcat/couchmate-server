package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class SportEvent(
  sportEventId: Option[Long],
  sportOrganizationId: Option[Long],
  sportEventTitle: String,
)

object SportEvent extends JsonConfig {
  implicit val format: Format[SportEvent] = Json.format[SportEvent]
}
