package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class UserNotifications(
  show: Seq[UserNotificationShow],
  series: Seq[UserNotificationSeries],
  teams: Seq[UserNotificationTeam]
)

object UserNotifications {
  implicit val format: Format[UserNotifications] = Json.format[UserNotifications]
}
