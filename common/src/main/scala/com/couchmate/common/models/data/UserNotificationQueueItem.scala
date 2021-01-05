package com.couchmate.common.models.data

import java.time.LocalDateTime
import java.util.UUID

import play.api.libs.json.{Format, Json}

case class UserNotificationQueueItem(
  notificationId: UUID,
  userId: UUID,
  airingId: String,
  hash: Option[String] = None,
  applicationPlatform: ApplicationPlatform,
  deliverAt: LocalDateTime,
  deliveredAt: Option[LocalDateTime] = None,
  success: Boolean = false,
  read: Boolean = false
)

object UserNotificationQueueItem {
  implicit val format: Format[UserNotificationQueueItem] = Json.format[UserNotificationQueueItem]
}