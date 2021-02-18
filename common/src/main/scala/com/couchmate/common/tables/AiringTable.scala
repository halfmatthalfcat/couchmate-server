package com.couchmate.common.tables

import java.time.LocalDateTime

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.Airing
import com.couchmate.common.util.slick.WithTableQuery

class AiringTable(tag: Tag) extends Table[Airing](tag, "airing") {
  def airingId: Rep[String] = column[String]("airing_id", O.PrimaryKey)
  def showId: Rep[Long] = column[Long]("show_id")
  def startTime: Rep[LocalDateTime] = column[LocalDateTime]("start_time", O.SqlType("timestamp"))
  def endTime: Rep[LocalDateTime] = column[LocalDateTime]("end_time", O.SqlType("timestamp"))
  def duration: Rep[Int] = column[Int]("duration")
  def isNew: Rep[Boolean] = column[Boolean]("is_new", O.Default(false))
  def * = (
    airingId.?,
    showId,
    startTime,
    endTime,
    duration,
    isNew,
  ) <> ((Airing.apply _).tupled, Airing.unapply)

  def showFk = foreignKey(
    "airing_show_fk",
    showId,
    ShowTable.table,
    )(
    _.showId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def showStartTimeIdx = index(
    "show_timing_idx",
    (showId, startTime, endTime),
    unique = true
  )

  def startTimeIdx = index(
    "airing_start_time_idx",
    startTime,
  )

  def endTimeIdx = index(
    "airing_end_time_idx",
    endTime
  )

  def startTimeEndTimeIdx = index(
    "airing_start_time_end_time_idx",
    (startTime, endTime)
  )
}

object AiringTable extends WithTableQuery[AiringTable] {
  private[couchmate] val table = TableQuery[AiringTable]

  private[couchmate] val insertOrGetAiringIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_airing_id(
              _airing_id VARCHAR,
              _show_id BIGINT,
              _start_time TIMESTAMP,
              _end_time TIMESTAMP,
              _duration INT,
              OUT __airing_id VARCHAR
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  airing_id
                  FROM    airing
                  WHERE   show_id = _show_id AND
                          start_time = _start_time AND
                          end_time = _end_time
                  FOR     SHARE
                  INTO    __airing_id;

                  EXIT WHEN FOUND;

                  INSERT INTO airing
                  (airing_id, show_id, start_time, end_time, duration)
                  VALUES
                  (_airing_id, _show_id, _start_time, _end_time, _duration)
                  ON CONFLICT (show_id, start_time, end_time) DO NOTHING
                  RETURNING airing_id
                  INTO __airing_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
