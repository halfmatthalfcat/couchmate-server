package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.Channel
import com.couchmate.common.util.slick.WithTableQuery

class ChannelTable(tag: Tag) extends Table[Channel](tag, "channel") {
  def channelId: Rep[Long] = column[Long]("channel_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def channelOwnerId: Rep[Option[Long]] = column[Option[Long]]("channel_owner_id")
  def callsign: Rep[String] = column[String]("callsign")
  def * = (
    channelId.?,
    extId,
    channelOwnerId,
    callsign,
  ) <> ((Channel.apply _).tupled, Channel.unapply)

  def channelOwnerIdFk = foreignKey(
    "channel_channel_owner_fk",
    channelOwnerId,
    ChannelOwnerTable.table,
  )(
    _.channelOwnerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
}

object ChannelTable extends WithTableQuery[ChannelTable] {
  private[couchmate] val table = TableQuery[ChannelTable]

  private[couchmate] val insertOrGetChannelIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_channel_id(
              _channel_id BIGINT,
              _ext_id BIGINT,
              _channel_owner_id BIGINT,
              _callsign VARCHAR,
              OUT __channel_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  channel_id
                  FROM    channel
                  WHERE   ext_id = _ext_id
                  FOR     SHARE
                  INTO    __channel_id;

                  EXIT WHEN FOUND;

                  INSERT INTO channel
                  (ext_id, channel_owner_id, callsign)
                  VALUES
                  (_ext_id, _channel_owner_id, _callsign)
                  ON CONFLICT (ext_id) DO NOTHING
                  RETURNING channel_id
                  INTO __channel_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
