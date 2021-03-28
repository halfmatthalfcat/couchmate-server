package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.ProviderChannel
import com.couchmate.common.util.slick.WithTableQuery

class ProviderChannelTable(tag: Tag) extends Table[ProviderChannel](tag, "provider_channel") {
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id", O.PrimaryKey, O.AutoInc)
  def providerId: Rep[Long] = column[Long]("provider_id")
  def channelId: Rep[Long] = column[Long]("channel_id")
  def channel: Rep[String] = column[String]("channel")
  def * = (
    providerChannelId.?,
    providerId,
    channelId,
    channel
  ) <> ((ProviderChannel.apply _).tupled, ProviderChannel.unapply)

  def providerFk = foreignKey(
    "provider_channel_provider_fk",
    providerId,
    ProviderTable.table,
    )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def channelFk = foreignKey(
    "provider_channel_channel_fk",
    channelId,
    ChannelTable.table,
    )(
    _.channelId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def oldProviderChannelIdx = index(
    "provider_channel_idx",
    (providerId, channelId),
  )

  def providerChannelIdx = index(
    "provider_channel_idx",
    (providerId, channelId),
    unique = true
  )
}

object ProviderChannelTable extends WithTableQuery[ProviderChannelTable] {
  private[couchmate] val table = TableQuery[ProviderChannelTable]

  private[couchmate] val insertOrGetProviderChannelIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_provider_channel_id(
              _provider_channel_id BIGINT,
              _provider_id BIGINT,
              _channel_id BIGINT,
              _channel VARCHAR,
              OUT __provider_channel_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  provider_channel_id
                  FROM    provider_channel
                  WHERE   provider_id = _provider_id
                  AND     channel_id = _channel_id
                  FOR     SHARE
                  INTO    __provider_channel_id;

                  EXIT WHEN FOUND;

                  INSERT INTO provider_channel
                  (provider_id, channel_id, channel)
                  VALUES
                  (_provider_id, _channel_id, _channel)
                  ON CONFLICT (provider_id, channel_id) DO NOTHING
                  RETURNING provider_channel_id
                  INTO __provider_channel_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql
          """
}
