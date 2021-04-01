package com.couchmate.migration.db

import com.couchmate.common.db.PgProfile.api._
import slick.migration.api._

import scala.concurrent.{ExecutionContext, Future}

case class MigrationItem[T <: Table[_]](migrationId: Long, table: TableQuery[T])
  (migration: MigrationItem.CMMigration[T])(sideEffects: () => Future[_] = () => Future.successful()) {
  private[this] implicit val profile: PostgresDialect = new PostgresDialect

  def up(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Unit] = for {
    _ <- db.run(migration(TableMigration(table))())
    _ = System.out.println("Schema applied")
    _ <- sideEffects()
  } yield ()
  def down(
    implicit
    db: Database
  ): Future[Unit] = db.run(migration(TableMigration(table)).reverse())
}

object MigrationItem {
  implicit def ordering: Ordering[MigrationItem[_]] =
    Ordering.by(_.migrationId)

  type CMMigration[T <: Table[_]] =
    TableMigration[T, TableMigration.Action.Reversible] => TableMigration[T, TableMigration.Action.Reversible]
}