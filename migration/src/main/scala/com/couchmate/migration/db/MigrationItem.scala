package com.couchmate.migration.db

import com.couchmate.common.db.PgProfile.api._
import slick.migration.api._

case class MigrationItem[T <: Table[_]](migrationId: Long, table: TableQuery[T])
  (migration: MigrationItem.CMMigration[T])(sideEffects: DBIO[_]*) extends PostgresDialect {
  private[this] implicit val profile: PostgresDialect = new PostgresDialect

  def up: DBIO[Unit] = migration(TableMigration(table))() andThen DBIO.seq(sideEffects: _*)
  def down: DBIO[Unit] = migration(TableMigration(table)).reverse()
}

object MigrationItem {
  implicit def ordering: Ordering[MigrationItem[_]] =
    Ordering.by(_.migrationId)

  type CMMigration[T <: Table[_]] =
    TableMigration[T, TableMigration.Action.Reversible] => TableMigration[T, TableMigration.Action.Reversible]
}