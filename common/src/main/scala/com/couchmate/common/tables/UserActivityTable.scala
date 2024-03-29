package com.couchmate.common.tables

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{UserActivity, UserActivityType}
import com.couchmate.common.util.slick.WithTableQuery

class UserActivityTable(tag: Tag) extends Table[UserActivity](tag, "user_activity") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def action: Rep[UserActivityType] = column[UserActivityType]("action")
  def created: Rep[LocalDateTime] = column[LocalDateTime]("created", O.SqlType("timestamp"))
  def deviceId: Rep[Option[String]] = column[Option[String]]("device_id")
  def os: Rep[Option[String]] = column[Option[String]]("os")
  def osVersion: Rep[Option[String]] = column[Option[String]]("os_version")
  def brand: Rep[Option[String]] = column[Option[String]]("brand")
  def model: Rep[Option[String]] = column[Option[String]]("model")

  def * = (
    userId,
    action,
    created,
    deviceId,
    os,
    osVersion,
    brand,
    model
  ) <> ((UserActivity.apply _).tupled, UserActivity.unapply)

  def userFk = foreignKey(
    "user_user_activity_fk",
    userId,
    UserTable.table,
    )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserActivityTable extends WithTableQuery[UserActivityTable] {
  private[couchmate] val table = TableQuery[UserActivityTable]
}
