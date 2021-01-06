package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserNotificationShow
import com.couchmate.common.util.slick.WithTableQuery

class UserNotificationShowTable(tag: Tag) extends Table[UserNotificationShow](tag, "user_notification_show") {
  def userId: Rep[UUID] = column("user_id", O.SqlType("uuid"))
  def airingId: Rep[String] = column("airing_id")
  def hash: Rep[Option[String]] = column("hash")
  def onlyNew: Rep[Boolean] = column("only_new")
  def created: Rep[LocalDateTime] = column("created")

  def * = (
    userId,
    airingId,
    hash,
    onlyNew,
    created
  ) <> ((UserNotificationShow.apply _).tupled, UserNotificationShow.unapply)

  def pk = primaryKey(
    "user_notification_show_pk",
    (userId, airingId)
  )

  def userIdFk = foreignKey(
    "user_notification_show_user",
    userId,
    UserTable.table
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def airingIdFk = foreignKey(
    "user_notification_show_airing",
    airingId,
    AiringTable.table
  )(
    _.airingId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object UserNotificationShowTable extends WithTableQuery[UserNotificationShowTable] {
  private[couchmate] val table: TableQuery[UserNotificationShowTable] =
    TableQuery[UserNotificationShowTable]
}
