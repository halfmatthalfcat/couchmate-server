package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ApplicationPlatform, UserNotificationQueueItem}
import com.couchmate.common.util.slick.WithTableQuery

class UserNotificationQueueTable(tag: Tag) extends Table[UserNotificationQueueItem](tag, "user_notification_queue") {
  def notificationId: Rep[UUID] = column("notification_id", O.PrimaryKey, O.SqlType("uuid"))
  def userId: Rep[UUID] = column("user_id", O.SqlType("uuid"))
  def airingId: Rep[String] = column("airing_id")
  def hash: Rep[Option[String]] = column("hash")
  def platform: Rep[ApplicationPlatform] = column("platform")
  def deliverAt: Rep[LocalDateTime] = column("deliver_at")
  def deliveredAt: Rep[Option[LocalDateTime]] = column("delivered_at")
  def success: Rep[Boolean] = column("success")
  def read: Rep[Boolean] = column("read")

  def * = (
    notificationId,
    userId,
    airingId,
    hash,
    platform,
    deliverAt,
    deliveredAt,
    success,
    read
  ) <> ((UserNotificationQueueItem.apply _).tupled, UserNotificationQueueItem.unapply)

  def uniqueIdx = index(
    "user_notification_unique_idx",
    (userId, airingId, platform, deliverAt),
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
}

object UserNotificationQueueTable extends WithTableQuery[UserNotificationQueueTable] {
  private[couchmate] val table: TableQuery[UserNotificationQueueTable] =
    TableQuery[UserNotificationQueueTable]
}