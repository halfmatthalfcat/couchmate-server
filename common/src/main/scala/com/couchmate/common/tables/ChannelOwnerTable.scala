package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.ChannelOwner
import com.couchmate.common.util.slick.WithTableQuery

class ChannelOwnerTable(tag: Tag) extends Table[ChannelOwner](tag, "channel_owner") {
  def channelOwnerId: Rep[Long] = column[Long]("channel_owner_id", O.PrimaryKey, O.AutoInc)
  def extId: Rep[Long] = column[Long]("ext_id")
  def callsign: Rep[String] = column[String]("callsign")

  def * = (
    channelOwnerId.?,
    extId,
    callsign
  ) <> ((ChannelOwner.apply _).tupled, ChannelOwner.unapply)

  def extIdx = index(
    "channel_owner_ext_id_idx",
    extId,
    unique = true
  )
}

object ChannelOwnerTable extends WithTableQuery[ChannelOwnerTable] {
  private[couchmate] val table = TableQuery[ChannelOwnerTable]

  private[couchmate] val insertOrGetChannelOwnerIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_channel_owner_id(
              _channel_owner_id BIGINT,
              _ext_id BIGINT,
              _callsign VARCHAR,
              OUT __channel_owner_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  channel_owner_id
                  FROM    channel_owner
                  WHERE   ext_id = _ext_id
                  FOR     SHARE
                  INTO    __channel_owner_id;

                  EXIT WHEN FOUND;

                  INSERT INTO channel_owner
                  (ext_id, callsign)
                  VALUES
                  (_ext_id, _callsign)
                  ON CONFLICT (ext_id) DO NOTHING
                  RETURNING channel_owner_id
                  INTO __channel_owner_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
