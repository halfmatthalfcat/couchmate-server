package com.couchmate.common.models.data

import com.couchmate.common.util.slick.RowParser
import play.api.libs.json.{Format, Json}
import slick.jdbc.GetResult

case class SportEvent(
  sportEventId: Option[Long],
  sportOrganizationId: Option[Long],
  sportEventTitle: String,
)

object SportEvent extends JsonConfig {
  implicit val format: Format[SportEvent] = Json.format[SportEvent]
  implicit val rowParser: GetResult[SportEvent] = RowParser[SportEvent]

}
