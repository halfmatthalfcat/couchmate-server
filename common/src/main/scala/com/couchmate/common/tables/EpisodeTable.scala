package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.Episode
import com.couchmate.common.util.slick.WithTableQuery

class EpisodeTable(tag: Tag) extends Table[Episode](tag, "episode") {
  def episodeId: Rep[Long] = column[Long]("episode_id", O.PrimaryKey, O.AutoInc)
  def seriesId: Rep[Long] = column[Long]("series_id")
  def season: Rep[Long] = column[Long]("season")
  def episode: Rep[Long] = column[Long]("episode")
  def * = (
    episodeId.?,
    seriesId.?,
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

  private[couchmate] val insertOrGetEpisodeIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_episode_id(
              _series_id BIGINT,
              _season BIGINT,
              _episode BIGINT,
              OUT _episode_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  episode_id
                  FROM    episode
                  WHERE   series_id = _series_id AND
                          season = _season AND
                          episode = _episode
                  FOR     SHARE
                  INTO    _episode_id;

                  EXIT WHEN FOUND;

                  INSERT INTO episode
                  (series_id, season, episode)
                  VALUES
                  (_series_id, _season, _episode)
                  ON CONFLICT (series_id, season, episode) DO NOTHING
                  RETURNING episode_id
                  INTO _episode_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
