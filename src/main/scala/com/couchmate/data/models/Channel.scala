package com.couchmate.data.models

import com.couchmate.data.schema.GenGetResult
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.GetResult

case class Channel(
  channelId: Option[Long],
  extId: Long,
  callsign: String,
) extends Product with Serializable

object Channel extends JsonConfig {
  implicit val format: OFormat[Channel] = Json.format[Channel]
  implicit val getResult: GetResult[Channel] = GenGetResult[Channel]
}
