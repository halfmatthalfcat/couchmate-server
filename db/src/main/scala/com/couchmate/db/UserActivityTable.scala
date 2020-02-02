package com.couchmate.db

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.models.{UserActivity, UserActivityType}
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class UserActivityTable(tag: Tag) extends Table[UserActivity](tag, "user_activity") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def action: Rep[UserActivityType] = column[UserActivityType]("action")
  def created: Rep[LocalDateTime] = column[LocalDateTime]("created", O.SqlType("timestamptz"))
  def * = (
    userId,
    action,
    created,
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

object UserActivityTable extends Slickable[UserActivityTable] {
  val table = TableQuery[UserActivityTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.action,
      _.created,
    ).addForeignKeys(
      _.userFk,
    )
}
