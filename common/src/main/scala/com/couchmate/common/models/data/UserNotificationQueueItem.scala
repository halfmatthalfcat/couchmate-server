package com.couchmate.common.models.data

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserNotificationQueueItem(
  notificationId: UUID,
  userId: UUID,
  airingId: String,
  notificationType: UserNotificationQueueItemType,
  hash: String,
  title: String,
  callsign: Option[String],
  applicationPlatform: ApplicationPlatform,
  token: Option[String],
  deliverAt: LocalDateTime,
  deliveredAt: Option[LocalDateTime] = None,
  success: Boolean = false,
  read: Boolean = false,
  readAt: Option[LocalDateTime] = None
)

object UserNotificationQueueItem {
  implicit val format: Format[UserNotificationQueueItem] = Json.format[UserNotificationQueueItem]
}