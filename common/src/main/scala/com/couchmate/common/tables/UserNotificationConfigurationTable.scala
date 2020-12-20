package com.couchmate.common.tables

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ApplicationPlatform, UserNotificationConfiguration}
import com.couchmate.common.util.slick.WithTableQuery

class UserNotificationConfigurationTable(tag: Tag) extends Table[UserNotificationConfiguration](tag, "user_notification_configuration") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def platform: Rep[ApplicationPlatform] = column[ApplicationPlatform]("application_platform")
  def active: Rep[Boolean] = column[Boolean]("active")
  def token: Rep[Option[String]] = column[Option[String]]("token")

  def * = (
    userId,
    active,
    platform,
    token
  ) <> ((UserNotificationConfiguration.apply _).tupled, UserNotificationConfiguration.unapply)

  def pk = primaryKey(
    "user_notification_pk",
    (userId, platform)
  )

  def userIdFk = foreignKey(
    "user_notification_user_fk",
    userId,
    UserTable.table,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object UserNotificationConfigurationTable extends WithTableQuery[UserNotificationConfigurationTable] {
  private[couchmate] val table: TableQuery[UserNotificationConfigurationTable] =
    TableQuery[UserNotificationConfigurationTable]
}