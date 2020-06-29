package com.couchmate.common.tables

import java.time.LocalDateTime

import com.couchmate.common.db.PgProfile.api._
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
}

object ListingCacheTable extends WithTableQuery[ListingCacheTable] {
  private[couchmate] val table: TableQuery[ListingCacheTable] = TableQuery[ListingCacheTable]
}
