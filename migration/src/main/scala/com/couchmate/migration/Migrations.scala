package com.couchmate.migration

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.tables.{AiringTable, ChannelOwnerTable, ChannelTable, EpisodeTable, LineupTable, ListingCacheTable, ProviderChannelTable, ProviderOwnerTable, ProviderTable, SeriesTable, ShowTable, SportEventTable, SportEventTeamTable, SportOrganizationTable, SportOrganizationTeamTable, SportTeamTable}
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
    UserNotificationConfigurationMigrations.init,
    SportTeamMigrations.uniqueExtIndex,
    UserNotificationShowMigrations.init,
    UserNotificationSeriesMigrations.init,
    UserNotificationTeamMigrations.init,
    UserNotificationQueueMigrations.init,
    UserActivityMigrations.addDeviceId,
    UserNotificationQueueMigrations.addCallsign,
    UserNotificationShowMigrations.addChannelProviderAndActive,
    UserNotificationSeriesMigrations.addChannelProviderAndActive,
    UserNotificationTeamMigrations.addChannelProviderAndActive,
    UserNotificationQueueMigrations.addNotificationType,
    UserNotificationSeriesMigrations.addNameAndCallsign,
    UserNotificationShowMigrations.addNameAndCallsign,
    UserNotificationTeamMigrations.addName,
    SportOrganizationTeamMigrations.init,
    SportEventTeamMigrations.dropTeamId,
    SportEventTeamMigrations.addTeamOrgId,
    UserNotificationTeamMigrations.addSportOrgTeamFk,
    UserNotificationQueueMigrations.addReadAt,
    ListingCacheMigrations.providerStartTimeIdx,
    AiringMigrations.startTimeEndTimeIdx,
    UserActivityAnalyticsMigrations.init,
    ProviderOwnerMigrations.addIdx,
    ProviderMigrations.makeIdxUnique,
    ProviderMigrations.addDevice,
    UserChannelFavoriteMigrations.init,
    LineupMigrations.airingIdx,
    ProviderChannelMigrations.createUniqueIdx
  )

  val functions: Seq[DBIO[Int]] = Seq(
    SeriesTable.insertOrGetSeriesIdFunction,
    AiringTable.insertOrGetAiringIdFunction,
    EpisodeTable.insertOrGetEpisodeIdFunction,
    LineupTable.insertOrGetLineupIdFunction,
    ListingCacheTable.insertOrGetListingCacheIdFunction,
    ShowTable.insertOrGetShowIdFunction,
    SportEventTable.insertOrGetSportEventIdFunction,
    SportEventTeamTable.insertOrGetSportEventTeamIdFunction,
    SportOrganizationTable.insertOrGetSportOrganizationIdFunction,
    SportOrganizationTeamTable.insertOrGetSportOrganizationTeamIdFunction,
    SportTeamTable.insertOrGetSportTeamIdFunction,
    ProviderOwnerTable.insertOrGetProviderOwnerIdFunction,
    ProviderTable.insertOrGetProviderIdFunction,
    ChannelTable.insertOrGetChannelIdFunction,
    ChannelOwnerTable.insertOrGetChannelOwnerIdFunction,
    ProviderChannelTable.insertOrGetProviderChannelIdFunction
  )

  private[this] def applyMigrations()(
    implicit
    db: Database,
  ): Future[Unit] = ensureTableAndGet flatMap { currentMigrations =>
    System.out.println(s"Found ${currentMigrations.size} migrations already applied.")

    if (currentMigrations.size == migrations.size) {
      System.out.println("All migrations have already been applied, adding/updating functions.")
      db.run(DBIO.sequence(functions)).map(_ => ())
    } else {
      val newMigrations = migrations
        .filterNot(m => currentMigrations.exists(_.migrationId == m.migrationId))
        .toSeq
        .map(item => (for {
          _ <- item.up
          migrated <- MigrationDAO.addMigration(Migration(
            item.migrationId,
          ))
        } yield migrated).transactionally)


      System.out.println(s"Applying ${newMigrations.size} migrations and ${functions.size} functions.")

      for {
        _ <- db.run(DBIO.sequence(functions))
        _ <- db.run(DBIO.sequence(newMigrations))
      } yield ()
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