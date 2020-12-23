package com.couchmate.common.models.data

import play.api.libs.json.{Format, Json}

case class UserShowNotification(
  userShowNotificationId: Long,
  notificationType: UserShowNotificationType,
  id: Long
)

object UserShowNotification {
  implicit val format: Format[UserShowNotification] = Json.format[UserShowNotification]
}
