package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.Series
import com.couchmate.common.util.slick.WithTableQuery
import slick.jdbc.SQLActionBuilder
import slick.sql.SqlStreamingAction

class SeriesTable(tag: Tag) extends Table[Series](tag, "series") {
  def seriesId: Rep[Long] = column[Long]("series_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id", O.Unique)
  def seriesName: Rep[String] = column[String]("series_name")
  def totalSeasons: Rep[Option[Long]] = column[Option[Long]]("total_seasons")
  def totalEpisodes: Rep[Option[Long]] = column[Option[Long]]("total_episodes")
  def * = (
    seriesId.?,
    extId,
    seriesName,
    totalSeasons,
    totalEpisodes,
  ) <> ((Series.apply _).tupled, Series.unapply)

  def extIdIdx = index(
    "series_ext_idx",
    extId,
    unique = true
  );
}

object SeriesTable extends WithTableQuery[SeriesTable] {
  private[couchmate] val table = TableQuery[SeriesTable]

  private[couchmate] val insertOrGetSeriesIdFunction: DBIO[Int] =
    sqlu"""
        CREATE OR REPLACE FUNCTION insert_or_get_series_id(
          _ext_id BIGINT,
          _series_name VARCHAR,
          _total_seasons INT,
          _total_episodes Int,
          OUT _series_id BIGINT
        ) AS
        $$func$$
          BEGIN
            LOOP
              SELECT  series_id
              FROM    series
              WHERE   ext_id = _ext_id
              FOR     SHARE
              INTO    _series_id;

              EXIT WHEN FOUND;

              INSERT INTO series
              (ext_id, series_name, total_seasons, total_episodes)
              VALUES
              (_ext_id, _series_name, _total_seasons, _total_episodes)
              ON CONFLICT (ext_id) DO NOTHING
              RETURNING series_id
              INTO _series_id;

              EXIT WHEN FOUND;
            END LOOP;
          END;
        $$func$$ LANGUAGE plpgsql;
      """
}
