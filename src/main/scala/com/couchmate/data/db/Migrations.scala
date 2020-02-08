package com.couchmate.data.db

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Migrations extends LazyLogging {
  private[this] val tables: Seq[Slickable[_]] = Seq(
      UserTable,
      ProviderOwnerTable,
      ProviderTable,
      UserProviderTable,
      ChannelTable,
      ProviderChannelTable,
      SeriesTable,
      EpisodeTable,
      SportOrganizationTable,
      SportEventTable,
      ShowTable,
      AiringTable,
      ZipProviderTable,
      RoomActivityTable,
      UserMetaTable,
      UserActivityTable,
      UserExtTable,
      UserPrivateTable,
      ListingCacheTable,
    )

  private[this] def applySchema()(
    implicit
    db: Database,
  ): Future[Seq[Unit]] = {
    Future.sequence(
      tables.map { table: Slickable[_] =>
        db.run(table.init())
      }
    )
  }

  private[this] def dropSchema()(
    implicit
    db: Database,
  ): Future[Seq[Unit]] = {
    Future.sequence(
      tables.reverse.map { table: Slickable[_] =>
        db.run(table.schema.dropIfExists)
      }
    )
  }

  private[this] def resetSchema()(
    implicit
    db: Database,
  ): Future[Unit] = for {
    _ <- dropSchema()
    _ <- applySchema()
  } yield ()

  private[this] def truncateSchema()(
    implicit
    db: Database,
  ): Future[Seq[Unit]] = {
    Future.sequence(
      tables.reverse.map { table: Slickable[_] =>
        db.run(table.schema.truncate)
      }
    )
  }

  def main(args: Array[String]): Unit = {
    args match {
      case Array("apply") =>
        logger.info("Applying schema")

        implicit val db: Database =
          Database.forConfig("db")

        Await.result(
          applySchema(),
          Duration.Inf,
        )

        db.close()

      case Array("truncate") =>
        logger.info("Truncating schema")

        implicit val db: Database =
          Database.forConfig("db")

        Await.result(
          truncateSchema(),
          Duration.Inf,
        )

        db.close()

      case Array("reset") =>
        logger.info("Resetting schema")

        implicit val db: Database =
          Database.forConfig("db")

        Await.result(
          resetSchema(),
          Duration.Inf,
        )

        db.close()

      case args: Array[String] =>
        logger.info(s"Couldn't find command ${args.mkString(" ")}")
    }
  }
}