package com.couchmate.data.db.table

import java.time.LocalDateTime

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.{Airing, ListingCache}
import com.couchmate.external.gracenote.models.GracenoteAiring
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

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

object ListingCacheTable extends Slickable[ListingCacheTable] {
  private[db] val table: TableQuery[ListingCacheTable] = TableQuery[ListingCacheTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.listingCacheId,
      _.providerChannelId,
      _.startTime,
      _.airings,
    ).addForeignKeys(
      _.providerChannelFk,
    )

  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] =
    Option.empty
}
