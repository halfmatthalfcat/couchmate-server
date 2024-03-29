package com.couchmate.common.models.api.user

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.models.data.{UserNotifications, UserRole}
import play.api.libs.json.{Json, OFormat}

case class User(
  userId: UUID,
  created: LocalDateTime,
  verified: Boolean,
  role: UserRole,
  username: String,
  email: Option[String],
  token: String,
  mutes: Seq[UserMute],
  wordMutes: Seq[String],
  notificationsEnabled: Boolean,
  notifications: UserNotifications,
  favoriteChannels: Seq[Long]
)

object User {
  implicit val format: OFormat[User] = Json.format[User]
}
