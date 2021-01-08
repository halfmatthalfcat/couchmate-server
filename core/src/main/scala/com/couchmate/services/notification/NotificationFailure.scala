package com.couchmate.services.notification

import java.util.UUID

case class NotificationFailure(
  notificationId: UUID,
  cause: Option[String],
  description: Option[String]
)
