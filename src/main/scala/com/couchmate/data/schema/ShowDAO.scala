package com.couchmate.data.schema

import java.time.OffsetDateTime

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.Show
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class ShowDAO(tag: Tag) extends Table[Show](tag, "show") {
  def showId: Rep[Long] = column[Long]("show_id", O.PrimaryKey, O.AutoInc)
  def sourceId: Rep[Long] = column[Long]("source_id")
  def extId: Rep[Long] = column[Long]("ext_id")
  def `type`: Rep[String] = column[String]("type")
  def episodeId: Rep[Long] = column[Long]("episode_id")
  def sportEventId: Rep[Long] = column[Long]("sport_event_id")
  def title: Rep[String] = column[String]("title")
  def description: Rep[String] = column[String]("description")
  def originalAirDate: Rep[OffsetDateTime] = column[OffsetDateTime]("original_air_date", O.SqlType("timestamptz"))
  def * = (
    showId.?,
    sourceId,
    extId,
    `type`,
    episodeId.?,
    sportEventId.?,
    title,
    description,
    originalAirDate.?,
  ) <> ((Show.apply _).tupled, Show.unapply)

  def sourceFk = foreignKey(
    "show_source_fk",
    sourceId,
    SourceDAO.sourceTable,
  )(
    _.sourceId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

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

  def sourceExtIdx = index(
    "show_source_ext_idx",
    (sourceId, extId),
    unique = true
  )
}

object ShowDAO {
  val showTable = TableQuery[ShowDAO]

  val init = TableMigration(showTable)
    .create
    .addColumns(
      _.showId,
      _.sourceId,
      _.extId,
      _.`type`,
      _.episodeId,
      _.sportEventId,
      _.title,
      _.description,
      _.originalAirDate,
    ).addForeignKeys(
      _.sourceFk,
      _.episodeFk,
      _.sportFk,
    ).addIndexes(
      _.sourceExtIdx,
    )

  def getShow()(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[Show], NotUsed] = Slick.flowWithPassThrough { showId =>
    showTable.filter(_.showId === showId).result.headOption
  }

  def getShowFromSourceAndExt()(
    implicit
    session: SlickSession,
  ): Flow[(Long, Long), Option[Show], NotUsed] = Slick.flowWithPassThrough {
    case (sourceId, extId) => showTable.filter { show =>
      show.sourceId === sourceId &&
      show.extId === extId
    }.result.headOption
  }

  def upsertShow()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[Show, Show, NotUsed] = Slick.flowWithPassThrough {
    case show @ Show(None, _, _, _, _, _, _, _, _) =>
      (showTable returning showTable) += show
    case show @ Show(Some(showId), _, _, _, _, _, _, _, _) => for {
      _ <- showTable.filter(_.showId === showId).update(show)
      s <- showTable.filter(_.showId === showId).result.head
    } yield s
  }
}
