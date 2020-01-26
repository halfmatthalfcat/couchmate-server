package com.couchmate.data.schema

import PgProfile.api._
import com.couchmate.data.models.Episode
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class EpisodeDAO(tag: Tag) extends Table[Episode](tag, "episode") {
  def episodeId: Rep[Long] = column[Long]("episode_id", O.PrimaryKey, O.AutoInc)
  def seriesId: Rep[Long] = column[Long]("series_id")
  def season: Rep[Option[Int]] = column[Option[Int]]("season")
  def episode: Rep[Option[Int]] = column[Option[Int]]("episode")
  def * = (
    episodeId.?,
    seriesId,
    season,
    episode,
  ) <> ((Episode.apply _).tupled, Episode.unapply)

  def seriesFk = foreignKey(
    "series_episode_fk",
    seriesId,
    SeriesDAO.seriesTable,
  )(
    _.seriesId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object EpisodeDAO {
  val episodeTable = TableQuery[EpisodeDAO]

  val init = TableMigration(episodeTable)
    .create
    .addColumns(
      _.episodeId,
      _.seriesId,
      _.season,
      _.episode,
    ).addForeignKeys(
      _.seriesFk,
    )

  def getEpisode(episodeId: Long)(
    implicit
    db: Database,
  ): Future[Option[Episode]] = {
    db.run(episodeTable.filter(_.episodeId === episodeId).result.headOption)
  }

  def upsertEpisode(episode: Episode)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Episode] = {
    episode match {
      case Episode(None, _, _, _) =>
        db.run((episodeTable returning episodeTable) += episode)
      case Episode(Some(episodeId), _, _, _) => for {
        _ <- db.run(episodeTable.filter(_.episodeId === episodeId).update(episode))
        e <- db.run(episodeTable.filter(_.episodeId === episodeId).result.head)
      } yield e
    }
  }
}
