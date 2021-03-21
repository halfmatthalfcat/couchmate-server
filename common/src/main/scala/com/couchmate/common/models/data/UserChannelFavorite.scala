package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

import java.util.UUID

case class UserChannelFavorite(
  userId: UUID,
  providerChannelId: Long
)

object UserChannelFavorite {
  implicit val format: Format[UserChannelFavorite] = Json.format[UserChannelFavorite]
}
