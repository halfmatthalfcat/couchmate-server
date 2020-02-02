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
}
