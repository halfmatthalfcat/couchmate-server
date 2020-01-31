package com.couchmate.db

import java.time.LocalDateTime

import com.couchmate.common.models.{Airing, ListingCache}
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class ListingCacheTable(tag: Tag) extends Table[ListingCache](tag, "listing_cache") {
  def listingCacheId: Rep[Long] = column[Long]("listing_cache_id", O.PrimaryKey, O.AutoInc)
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id")
  def startTime: Rep[LocalDateTime] = column[LocalDateTime]("start_time", O.SqlType("timestamp"))
  def airings: Rep[Seq[Airing]] = column[Seq[Airing]]("airings", O.SqlType("jsonb"))
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

object ListingCacheTable extends Slickable[ListingCacheTable] {
  val table: TableQuery[ListingCacheTable] = TableQuery[ListingCacheTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.listingCacheId,
      _.providerChannelId,
      _.startTime,
      _.airings,
    ).addForeignKeys(
      _.providerChannelFk,
    )

//  private[this] lazy val getListingCacheCompiled = Compiled { (providerChannelId: Rep[Long], startTime: Rep[LocalDateTime]) =>
//    listingCacheTable.filter { lc =>
//      lc.providerChannelId === providerChannelId &&
//      lc.startTime === startTime
//    }
//  }
//
//  def getListingCache(
//    providerChannelId: Long,
//    startTime: LocalDateTime,
//  ): AppliedCompiledFunction[(Long, LocalDateTime), Query[ListingCacheTable, ListingCacheTable, Seq], Seq[ListingCacheTable]] = {
//    getListingCacheCompiled(providerChannelId, startTime)
//  }
//
//  def upsertListingCache(lc: ListingCacheTable): SqlStreamingAction[Vector[ListingCacheTable], ListingCacheTable, Effect] = {
//    sql"""
//         INSERT INTO listing_cache
//         (listing_cache_id, provider_channel_id, start_time, airings)
//         VALUES
//         (${lc.listingCacheId}, ${lc.providerChannelId}, ${lc.startTime}, ${lc.airings})
//         ON CONFLICT (listing_cache_id)
//         DO UPDATE SET
//            provider_channel_id = ${lc.providerChannelId},
//            start_time = ${lc.startTime},
//            airings = ${lc.airings}
//         RETURNING *
//       """.as[ListingCacheTable]
//  }
}
