package com.couchmate.data.schema

import PgProfile.api._
import com.couchmate.data.models.Episode
import slick.lifted.{AppliedCompiledFunction, Tag}
import slick.migration.api.TableMigration
import slick.sql.SqlStreamingAction

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

  private[this] lazy val getEpisodeCompiled = Compiled { (episodeId: Rep[Long]) =>
    episodeTable.filter(_.episodeId === episodeId)
  }

  def getEpisode(episodeId: Long): AppliedCompiledFunction[Long, Query[EpisodeDAO, Episode, Seq], Seq[Episode]] = {
    getEpisodeCompiled(episodeId)
  }

  def upsertEpisode(e: Episode): SqlStreamingAction[Vector[Episode], Episode, Effect] = {
    sql"""
         INSERT INTO episode
         (episode_id, series_id, season, episode)
         VALUES
         (${e.episodeId}, ${e.seriesId}, ${e.season}, ${e.episode})
         ON CONFLICT (episode_id)
         DO UPDATE SET
            series_id = ${e.seriesId},
            season = ${e.season},
            episode = ${e.episode}
         RETURNING *
       """.as[Episode]
  }
}
