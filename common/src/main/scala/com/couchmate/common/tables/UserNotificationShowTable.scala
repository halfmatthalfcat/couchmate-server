package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserNotificationShow
import com.couchmate.common.util.slick.WithTableQuery

class UserNotificationShowTable(tag: Tag) extends Table[UserNotificationShow](tag, "user_notification_show") {
  def userId: Rep[UUID] = column("user_id", O.SqlType("uuid"))
  def airingId: Rep[String] = column("airing_id")
  def providerChannelId: Rep[Long] = column("provider_channel_id")
  def name: Rep[String] = column("name")
  def callsign: Rep[String] = column("callsign")
  def hash: Rep[String] = column("hash")
  def active: Rep[Boolean] = column("active")
  def onlyNew: Rep[Boolean] = column("only_new")
  def created: Rep[LocalDateTime] = column("created")

  def * = (
    userId,
    airingId,
    providerChannelId,
    name,
    callsign,
    hash,
    onlyNew,
    active,
    created
  ) <> ((UserNotificationShow.apply _).tupled, UserNotificationShow.unapply)

  def pk = primaryKey(
    "user_notification_show_pk",
    (userId, airingId, providerChannelId)
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

  def providerChannelFk = foreignKey(
    "user_notification_series_provider_channel_fk",
    providerChannelId,
    ProviderChannelTable.table
  )(
    _.providerChannelId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  // deprecated
  def pkOld = primaryKey(
    "user_notification_show_pk",
    (userId, airingId)
  )
}

object UserNotificationShowTable extends WithTableQuery[UserNotificationShowTable] {
  private[couchmate] val table: TableQuery[UserNotificationShowTable] =
    TableQuery[UserNotificationShowTable]
}
