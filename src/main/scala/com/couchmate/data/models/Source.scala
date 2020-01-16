package com.couchmate.data.models

import play.api.libs.json.{Json, OFormat}

case class Source(
  sourceId: Option[Long],
  name: String
) extends Product with Serializable

object Source extends JsonConfig {
  implicit val format: OFormat[Source] = Json.format[Source]
}
