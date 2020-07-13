package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Episode
import com.couchmate.common.util.slick.WithTableQuery

class EpisodeTable(tag: Tag) extends Table[Episode](tag, "episode") {
  def episodeId: Rep[Long] = column[Long]("episode_id", O.PrimaryKey, O.AutoInc)
  def seriesId: Rep[Option[Long]] = column[Option[Long]]("series_id")
  def season: Rep[Long] = column[Long]("season")
  def episode: Rep[Long] = column[Long]("episode")
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

  def seriesIdx = index(
    "series_idx",
    (seriesId, season, episode),
    unique = true
  )
}

object EpisodeTable extends WithTableQuery[EpisodeTable] {
  private[couchmate] val table = TableQuery[EpisodeTable]
}
