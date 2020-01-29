package com.couchmate.data.models

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class SportEvent(
  sportEventId: Option[Long],
  sportOrganizationId: Long,
  sportEventTitle: String,
) extends Product with Serializable

object SportEvent extends JsonConfig {
  implicit val format: OFormat[SportEvent] = Json.format[SportEvent]
  implicit val getResult: GetResult[SportEvent] = GenGetResult[SportEvent]
}
