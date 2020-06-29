package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.Episode
import com.couchmate.common.util.slick.WithTableQuery

class EpisodeTable(tag: Tag) extends Table[Episode](tag, "episode") {
  def episodeId: Rep[Long] = column[Long]("episode_id", O.PrimaryKey, O.AutoInc)
  def seriesId: Rep[Option[Long]] = column[Option[Long]]("series_id")
  def season: Rep[Option[Long]] = column[Option[Long]]("season")
  def episode: Rep[Option[Long]] = column[Option[Long]]("episode")
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

object EpisodeTable extends WithTableQuery[EpisodeTable] {
  private[couchmate] val table = TableQuery[EpisodeTable]
}
