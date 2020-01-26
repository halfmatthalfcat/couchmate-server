package com.couchmate.data.schema

import java.time.OffsetDateTime

import PgProfile.api._
import com.couchmate.data.models.Show
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class ShowDAO(tag: Tag) extends Table[Show](tag, "show") {
  def showId: Rep[Long] = column[Long]("show_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def `type`: Rep[String] = column[String]("type")
  def episodeId: Rep[Option[Long]] = column[Option[Long]]("episode_id")
  def sportEventId: Rep[Option[Long]] = column[Option[Long]]("sport_event_id")
  def title: Rep[String] = column[String]("title")
  def description: Rep[String] = column[String]("description")
  def originalAirDate: Rep[Option[OffsetDateTime]] = column[Option[OffsetDateTime]]("original_air_date", O.SqlType("timestamptz"))
  def * = (
    showId.?,
    extId,
    `type`,
    episodeId,
    sportEventId,
    title,
    description,
    originalAirDate,
  ) <> ((Show.apply _).tupled, Show.unapply)

  def episodeFk = foreignKey(
    "show_episode_fk",
    episodeId,
    EpisodeDAO.episodeTable,
  )(
    _.episodeId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def sportFk = foreignKey(
    "sport_episode_fk",
    sportEventId,
    SportEventDAO.sportEventTable,
  )(
    _.sportEventId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object ShowDAO {
  val showTable = TableQuery[ShowDAO]

  val init = TableMigration(showTable)
    .create
    .addColumns(
      _.showId,
      _.extId,
      _.`type`,
      _.episodeId,
      _.sportEventId,
      _.title,
      _.description,
      _.originalAirDate,
    ).addForeignKeys(
      _.episodeFk,
      _.sportFk,
    )

  def getShow(showId: Long)(
    implicit
    db: Database
  ): Future[Option[Show]] = {
    db.run(showTable.filter(_.showId === showId).result.headOption)
  }

  def getShowFromExt(extId: Long)(
    implicit
    db: Database,
  ): Future[Option[Show]] = {
    db.run(showTable.filter(_.extId === extId).result.headOption)
  }

  def upsertShow(show: Show)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Show] = {
    show match {
      case Show(None, _, _, _, _, _, _, _) =>
        db.run((showTable returning showTable) += show)
      case Show(Some(showId), _, _, _, _, _, _, _) => for {
        _ <- db.run(showTable.filter(_.showId === showId).update(show))
        s <- db.run(showTable.filter(_.showId === showId).result.head)
      } yield s
    }
  }
}
