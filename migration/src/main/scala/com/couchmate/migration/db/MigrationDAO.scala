package com.couchmate.migration.db

import com.couchmate.common.db.PgProfile.api._

import scala.concurrent.ExecutionContext

object MigrationDAO {
  private[migration] def migrationTableExists: DBIO[Boolean] =
    MigrationTable.table.exists.result

  private[migration] def ensureMigrationTableExists(
    implicit
    ec: ExecutionContext
  ): DBIO[Unit] = migrationTableExists flatMap {
    case true => DBIO.successful()
    case false => MigrationTable.init()
  }

  private[migration] def getMigrations: DBIO[Seq[Migration]] =
    MigrationTable.table.result

  private[migration] def addMigration(migration: Migration): DBIO[Migration] =
    (MigrationTable.table returning MigrationTable.table) += migration

  private[migration] def removeMigration(migrationId: Long): DBIO[Int] =
    MigrationTable.table.filter(_.migrationId === migrationId).delete
}
