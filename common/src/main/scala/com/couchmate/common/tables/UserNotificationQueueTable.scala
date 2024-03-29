package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ApplicationPlatform, UserNotificationQueueItem, UserNotificationQueueItemType}
import com.couchmate.common.util.slick.WithTableQuery

class UserNotificationQueueTable(tag: Tag) extends Table[UserNotificationQueueItem](tag, "user_notification_queue") {
  def notificationId: Rep[UUID] = column("notification_id", O.PrimaryKey, O.SqlType("uuid"))
  def userId: Rep[UUID] = column("user_id", O.SqlType("uuid"))
  def airingId: Rep[String] = column("airing_id")
  def notificationType: Rep[UserNotificationQueueItemType] = column("notification_type")
  def hash: Rep[String] = column("hash")
  def title: Rep[String] = column("title")
  def callsign: Rep[Option[String]] = column("callsign")
  def platform: Rep[ApplicationPlatform] = column("platform")
  def token: Rep[Option[String]] = column("token")
  def deliverAt: Rep[LocalDateTime] = column("deliver_at")
  def deliveredAt: Rep[Option[LocalDateTime]] = column("delivered_at")
  def success: Rep[Boolean] = column("success")
  def read: Rep[Boolean] = column("read")
  def readAt: Rep[Option[LocalDateTime]] = column("read_at")

  def * = (
    notificationId,
    userId,
    airingId,
    notificationType,
    hash,
    title,
    callsign,
    platform,
    token,
    deliverAt,
    deliveredAt,
    success,
    read,
    readAt
  ) <> ((UserNotificationQueueItem.apply _).tupled, UserNotificationQueueItem.unapply)

  def uniqueIdx = index(
    "user_notification_unique_idx",
    (userId, airingId, platform, notificationType),
    unique = true
  )

  def userIdFk = foreignKey(
    "user_notification_queue_user_fk",
    userId,
    UserTable.table
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def airingIdFk = foreignKey(
    "user_notification_queue_airing_fk",
    airingId,
    AiringTable.table
  )(
    _.airingId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  // deprecated
  def uniqueIdxOld = index(
    "user_notification_unique_idx",
    (userId, airingId, platform),
    unique = true
  )
}

object UserNotificationQueueTable extends WithTableQuery[UserNotificationQueueTable] {
  private[couchmate] val table: TableQuery[UserNotificationQueueTable] =
    TableQuery[UserNotificationQueueTable]
}