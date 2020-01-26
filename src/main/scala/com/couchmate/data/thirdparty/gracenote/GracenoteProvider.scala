package com.couchmate.data.thirdparty.gracenote

import play.api.libs.json.{Json, OFormat}

case class GracenoteProvider(
  lineupId: String,
  name: String,
  `type`: String,
  device: Option[String],
  location: Option[String],
  mso: Option[GracenoteProviderOwner],
) extends Product with Serializable

object GracenoteProvider {
  implicit val format: OFormat[GracenoteProvider] = Json.format[GracenoteProvider]
}
