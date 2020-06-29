package com.couchmate.common.models.thirdparty.gracenote

import play.api.libs.json.{Json, OFormat}

case class GracenoteProviderOwner(
  id: String,
  name: String,
) extends Product with Serializable

object GracenoteProviderOwner {
  implicit val gnProviderOwner: OFormat[GracenoteProviderOwner] = Json.format[GracenoteProviderOwner]
}
