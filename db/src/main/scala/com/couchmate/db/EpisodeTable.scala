package com.couchmate.db

import com.couchmate.common.models.Episode
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class EpisodeTable(tag: Tag) extends Table[Episode](tag, "episode") {
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
    SeriesTable.table,
    )(
    _.seriesId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object EpisodeTable extends Slickable[EpisodeTable] {
  val table = TableQuery[EpisodeTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.episodeId,
      _.seriesId,
      _.season,
      _.episode,
    ).addForeignKeys(
      _.seriesFk,
    )

//  private[this] lazy val getEpisodeCompiled = Compiled { (episodeId: Rep[Long]) =>
//    episodeTable.filter(_.episodeId === episodeId)
//  }
//
//  def getEpisode(episodeId: Long): AppliedCompiledFunction[Long, Query[EpisodeTable, EpisodeTable, Seq], Seq[EpisodeTable]] = {
//    getEpisodeCompiled(episodeId)
//  }
//
//  def upsertEpisode(e: EpisodeTable): SqlStreamingAction[Vector[EpisodeTable], EpisodeTable, Effect] = {
//    sql"""
//         INSERT INTO episode
//         (episode_id, series_id, season, episode)
//         VALUES
//         (${e.episodeId}, ${e.seriesId}, ${e.season}, ${e.episode})
//         ON CONFLICT (episode_id)
//         DO UPDATE SET
//            series_id = ${e.seriesId},
//            season = ${e.season},
//            episode = ${e.episode}
//         RETURNING *
//       """.as[EpisodeTable]
//  }
}
