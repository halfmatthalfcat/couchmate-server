package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.Lineup
import com.couchmate.common.util.slick.WithTableQuery

class LineupTable(tag: Tag) extends Table[Lineup](tag, "lineup") {
  def lineupId: Rep[Long] = column[Long]("lineup_id", O.AutoInc, O.PrimaryKey)
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id")
  def airingId: Rep[String] = column[String]("airing_id")
  def active: Rep[Boolean] = column[Boolean]("active")
  def * = (
    lineupId.?,
    providerChannelId,
    airingId,
    active,
  ) <> ((Lineup.apply _).tupled, Lineup.unapply)

  def providerChannelFk = foreignKey(
    "lineup_provider_channel_fk",
    providerChannelId,
    ProviderChannelTable.table,
    )(
      _.providerChannelId,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Restrict,
    )

  def airingFk = foreignKey(
    "lineup_airing_fk",
    airingId,
    AiringTable.table,
    )(
      _.airingId,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Restrict,
    )

  def pcAiringIdx = index(
    "provider_channel_airing_idx",
    (providerChannelId, airingId),
    unique = true
  )

  def airingIdx = index(
    "lineup_airing_idx",
    airingId
  )
}

object LineupTable extends WithTableQuery[LineupTable] {
  private[couchmate] val table = TableQuery[LineupTable]

  private[couchmate] val insertOrGetLineupIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_lineup_id(
              _provider_channel_id BIGINT,
              _airing_id VARCHAR,
              _active BOOL,
              OUT _lineup_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  lineup_id
                  FROM    lineup
                  WHERE   provider_channel_id = _provider_channel_id AND
                          airing_id = _airing_id
                  FOR     SHARE
                  INTO    _lineup_id;

                  EXIT WHEN FOUND;

                  INSERT INTO lineup
                  (provider_channel_id, airing_id, active)
                  VALUES
                  (_provider_channel_id, _airing_id, _active)
                  ON CONFLICT (provider_channel_id, airing_id) DO NOTHING
                  RETURNING lineup_id
                  INTO _lineup_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
