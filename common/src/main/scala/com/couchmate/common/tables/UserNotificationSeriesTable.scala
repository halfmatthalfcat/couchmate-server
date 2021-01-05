package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserNotificationSeries
import com.couchmate.common.util.slick.WithTableQuery

class UserNotificationSeriesTable(tag: Tag) extends Table[UserNotificationSeries](tag, "user_notification_series") {
  def userId: Rep[UUID] = column("user_id", O.SqlType("uuid"))
  def seriesId: Rep[Long] = column("series_id")
  def hash: Rep[Option[String]] = column("hash")
  def created: Rep[LocalDateTime] = column("created")

  def * = (
    userId,
    seriesId,
    hash,
    created
  ) <> ((UserNotificationSeries.apply _).tupled, UserNotificationSeries.unapply)

  def pk = primaryKey(
    "user_notification_series_pk",
    (userId, seriesId)
  )

  def userIdFk = foreignKey(
    "user_notification_series_user_fk",
    userId,
    UserTable.table
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def seriesIdFk = foreignKey(
    "user_notification_series_series_fk",
    seriesId,
    SeriesTable.table
  )(
    _.seriesId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object UserNotificationSeriesTable extends WithTableQuery[UserNotificationSeriesTable] {
  private[couchmate] val table: TableQuery[UserNotificationSeriesTable] =
    TableQuery[UserNotificationSeriesTable]
}
