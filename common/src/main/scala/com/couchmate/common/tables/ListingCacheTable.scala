package com.couchmate.common.tables

import java.time.LocalDateTime

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.ListingCache
import com.couchmate.common.models.thirdparty.gracenote.GracenoteAiring
import com.couchmate.common.util.slick.WithTableQuery

class ListingCacheTable(tag: Tag) extends Table[ListingCache](tag, "listing_cache") {
  def listingCacheId: Rep[Long] = column[Long]("listing_cache_id", O.PrimaryKey, O.AutoInc)
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id")
  def startTime: Rep[LocalDateTime] = column[LocalDateTime]("start_time", O.SqlType("timestamp"))
  def airings: Rep[Seq[GracenoteAiring]] = column[Seq[GracenoteAiring]]("airings", O.SqlType("jsonb[]"))
  def * = (
    listingCacheId.?,
    providerChannelId,
    startTime,
    airings,
  ) <> ((ListingCache.apply _).tupled, ListingCache.unapply)

  def providerChannelFk = foreignKey(
    "listing_cache_provider_channel_fk",
    providerChannelId,
    ProviderChannelTable.table,
    )(
    _.providerChannelId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  val idx = index(
    "listing_cache_provider_startTime_idx",
    (providerChannelId, startTime),
    unique = true
  )
}

object ListingCacheTable extends WithTableQuery[ListingCacheTable] {
  private[couchmate] val table: TableQuery[ListingCacheTable] = TableQuery[ListingCacheTable]

  private[couchmate] val insertOrGetListingCacheIdFunction: DBIO[Int] =
    sqlu"""
            CREATE OR REPLACE FUNCTION insert_or_get_listing_cache_id(
              _provider_channel_id BIGINT,
              _start_time TIMESTAMP,
              _airings JSONB[],
              OUT _listing_cache_id BIGINT
            ) AS
            $$func$$
              BEGIN
                LOOP
                  SELECT  listing_cache_id
                  FROM    listing_cache
                  WHERE   provider_channel_id = _provider_channel_id AND
                          start_time = _start_time
                  FOR     SHARE
                  INTO    _listing_cache_id;

                  EXIT WHEN FOUND;

                  INSERT INTO listing_cache
                  (provider_channel_id, start_time, airings)
                  VALUES
                  (_provider_channel_id, _start_time, _airings)
                  ON CONFLICT (provider_channel_id, start_time) DO NOTHING
                  RETURNING listing_cache_id
                  INTO _listing_cache_id;

                  EXIT WHEN FOUND;
                END LOOP;
              END;
            $$func$$ LANGUAGE plpgsql;
          """
}
