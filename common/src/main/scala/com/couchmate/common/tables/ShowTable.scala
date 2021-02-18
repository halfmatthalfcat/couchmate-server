package com.couchmate.common.tables

import java.time.LocalDateTime

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{Show, ShowType}
import com.couchmate.common.util.slick.WithTableQuery

class ShowTable(tag: Tag) extends Table[Show](tag, "show") {
  def showId: Rep[Long] = column[Long]("show_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id", O.Unique)
  def `type`: Rep[ShowType] = column[ShowType]("type")
  def episodeId: Rep[Option[Long]] = column[Option[Long]]("episode_id")
  def sportEventId: Rep[Option[Long]] = column[Option[Long]]("sport_event_id")
  def title: Rep[String] = column[String]("title")
  def description: Rep[String] = column[String]("description")
  def originalAirDate: Rep[Option[LocalDateTime]] = column[Option[LocalDateTime]]("original_air_date", O.SqlType("timestamp"))
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
    EpisodeTable.table,
    )(
    _.episodeId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def sportFk = foreignKey(
    "sport_episode_fk",
    sportEventId,
    SportEventTable.table,
    )(
    _.sportEventId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def extIdIdx = index(
    "show_ext_idx",
    extId,
    unique = true
  );
}

object ShowTable extends WithTableQuery[ShowTable] {
  private[couchmate] val table = TableQuery[ShowTable]

  private[couchmate] val insertOrGetShowIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_show_id(
              _ext_id BIGINT,
              _type VARCHAR,
              _episode_id BIGINT,
              _sport_event_id BIGINT,
              _title VARCHAR,
              _description VARCHAR,
              _original_air_date TIMESTAMP,
              OUT _show_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  show_id
                  FROM    show
                  WHERE   ext_id = _ext_id
                  FOR     SHARE
                  INTO    _show_id;

                  EXIT WHEN FOUND;

                  INSERT INTO show
                  (ext_id, type, episode_id, sport_event_id, title, description, original_air_date)
                  VALUES
                  (_ext_id, _type, _episode_id, _sport_event_id, _title, _description, _original_air_date)
                  ON CONFLICT (ext_id) DO NOTHING
                  RETURNING show_id
                  INTO _show_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
