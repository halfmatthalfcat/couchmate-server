package com.couchmate.data.thirdparty.gracenote

import play.api.libs.json.{Json, OFormat}

case class GracenoteProvider(
  lineupId: String,
  name: String,
  location: String,
  `type`: String,
) extends Product with Serializable

object GracenoteProvider {
  implicit val format: OFormat[GracenoteProvider] = Json.format[GracenoteProvider]
}
