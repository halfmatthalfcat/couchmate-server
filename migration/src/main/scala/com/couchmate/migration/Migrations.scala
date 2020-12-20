package com.couchmate.migration

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.migration.db.{Migration, MigrationDAO, MigrationItem, MigrationTable}
import com.couchmate.migration.migrations._
import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable.SortedSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Migrations extends LazyLogging {
  private[this] def migrations(implicit db: Database): SortedSet[MigrationItem[_]] = SortedSet(
    UserMigrations.init,
    ProviderOwnerMigrations.init,
    ProviderMigrations.init,
    UserProviderMigrations.init,
    ChannelOwnerMigrations.init,
    ChannelMigrations.init,
    ProviderChannelMigrations.init,
    SeriesMigrations.init,
    EpisodeMigrations.init,
    SportOrganizationMigrations.init,
    SportEventMigrations.init,
    ShowMigrations.init,
    AiringMigrations.init,
    ZipProviderMigrations.init,
    RoomActivityMigrations.init,
    UserMetaMigrations.init,
    UserActivityMigrations.init,
    UserExtMigrations.init,
    UserPrivateMigrations.init,
    UserMuteMigrations.init,
    ListingCacheMigrations.init,
    LineupMigrations.init,
    JournalMigrations.init,
    SnapshotMigrations.init,
    ListingJobMigrations.init,
    UserActivityMigrations.addDeviceMetrics,
    UserReportMigrations.init,
    UserWordBlockMigrations.init,
    AiringMigrations.addIsNew,
    SportTeamMigrations.init,
    SportEventTeamMigrations.init,
    UserNotificationConfigurationMigrations.init
  )

  private[this] def applyMigrations()(
    implicit
    db: Database,
  ): Future[Seq[Migration]] = ensureTableAndGet flatMap { currentMigrations =>
    System.out.println(s"Found ${currentMigrations.size} migrations already applied.")

    if (currentMigrations.size == migrations.size) {
      System.out.println("All migrations have already been applied, exiting.")
      Future.successful(Seq.empty)
    } else {
      val newMigrations = migrations
        .drop(currentMigrations.size)
        .toSeq
        .map(item => (for {
          _ <- item.up
          migrated <- MigrationDAO.addMigration(Migration(
            item.migrationId,
          ))
        } yield migrated).transactionally)


      System.out.println(s"Applying ${newMigrations.size} migrations.")

      db.run(DBIO.sequence(newMigrations))
    }
  }

  private[this] def dropMigrations()(
    implicit
    db: Database,
  ): Future[Seq[Unit]] = ensureTableAndGet flatMap { currentMigrations =>
    System.out.println(s"Found ${currentMigrations.size} migrations.")

    if (currentMigrations.isEmpty) {
      System.out.println("There are no migrations applied, stopping.")
      Future.successful(Seq.empty)
    } else {
      val newMigrations = migrations
        .take(currentMigrations.size)
        .toSeq.reverse
        .map(item => (for {
          _ <- item.down
          _ <- MigrationDAO.removeMigration(item.migrationId)
        } yield ()).transactionally)

      System.out.println(s"Unapplying ${newMigrations.size} migrations.")

      db.run(DBIO.sequence(newMigrations))
    }
  }

  private[this] def resetSchema()(
    implicit
    db: Database,
  ): Future[Unit] = for {
    _ <- dropMigrations
    _ <- applyMigrations
  } yield ()

//  private[this] def truncateSchema()(
//    implicit
//    db: Database,
//  ): Future[Seq[Unit]] = {
//    Future.sequence(
//      tables.reverse.map { table: Slickable[_] =>
//        db.run(table.schema.truncate)
//      }
//    )
//  }
//
//  private[this] def seed()(
//    implicit
//    db: Database,
//  ): Future[Unit] = {
//    Future.sequence(
//      tables
//        .map(_.seed)
//        .collect {
//          case Some(s) => db.run(s)
//        }
//    ).map(_ => ())
//  }

  def main(args: Array[String]): Unit = {
    args match {
      case Array("apply") =>
        System.out.println("Applying schema")

        implicit val db: Database =
          Database.forConfig("db")

        Await.result(
          applyMigrations(),
          Duration.Inf,
        )

        db.close()

      case Array("reset") =>
        System.out.println("Resetting schema")

        implicit val db: Database =
          Database.forConfig("db")

        Await.result(
          resetSchema(),
          Duration.Inf,
        )

        db.close()

      case Array("drop") =>
        System.out.println("Resetting schema")

        implicit val db: Database =
          Database.forConfig("db")

        Await.result(
          dropMigrations(),
          Duration.Inf,
        )

        db.close()

      case args: Array[String] =>
        System.out.println(s"Couldn't find command ${args.mkString(" ")}")
    }
  }

  private[this] def ensureTableAndGet()(
    implicit
    db: Database
  ): Future[Seq[Migration]] = for {
    _ <- db.run(MigrationTable.schema.createIfNotExists)
    currentMigrations <- db.run(MigrationDAO.getMigrations)
  } yield currentMigrations
}