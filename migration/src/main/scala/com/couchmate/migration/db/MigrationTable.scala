package com.couchmate.migration.db

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.db.PgProfile
import slick.migration.api.TableMigration

import java.time.LocalDateTime

import scala.concurrent.ExecutionContext

class MigrationTable(tag: Tag) extends Table[Migration](tag, "__migrations__") {
  def migrationId: Rep[Long] = column[Long]("migration_id", O.PrimaryKey)
  def dateApplied: Rep[LocalDateTime] = column[LocalDateTime]("date_applied", O.SqlType("timestamp"))

  def * = (
    migrationId,
    dateApplied
  ) <> ((Migration.apply _).tupled, Migration.unapply)
}

object MigrationTable {
  private[couchmate] val table = TableQuery[MigrationTable]

  private[couchmate] val schema: PgProfile.SchemaDescription = table.schema

  private[couchmate] val init = TableMigration(table)
    .create
    .addColumns(
      _.migrationId,
      _.dateApplied
    )

  private[couchmate] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] =
    Option.empty
}