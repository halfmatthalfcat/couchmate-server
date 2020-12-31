package com.couchmate.common.models.data

import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserNotificationSeries(
  userId: UUID,
  seriesId: Long
)

object UserNotificationSeries {
  implicit val format: Format[UserNotificationSeries] = Json.format[UserNotificationSeries]
}
