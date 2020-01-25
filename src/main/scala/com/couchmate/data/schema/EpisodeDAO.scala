package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.Episode
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class EpisodeDAO(tag: Tag) extends Table[Episode](tag, "episode") {
  def episodeId: Rep[Long] = column[Long]("episode_id", O.PrimaryKey, O.AutoInc)
  def seriesId: Rep[Long] = column[Long]("series_id")
  def season: Rep[Int] = column[Int]("season")
  def episode: Rep[Int] = column[Int]("episode")
  def * = (
    episodeId.?,
    seriesId,
    season.?,
    episode.?,
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

  def getEpisode()(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[Episode], NotUsed] = Slick.flowWithPassThrough { episodeId =>
    episodeTable.filter(_.episodeId === episodeId).result.headOption
  }

  def upsertEpisode()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[Episode, Episode, NotUsed] = Slick.flowWithPassThrough {
    case episode @ Episode(None, _, _, _) =>
      (episodeTable returning episodeTable) += episode
    case episode @ Episode(Some(episodeId), _, _, _) => for {
      _ <- episodeTable.filter(_.episodeId === episodeId).update(episode)
      e <- episodeTable.filter(_.episodeId === episodeId).result.head
    } yield e
  }
}
