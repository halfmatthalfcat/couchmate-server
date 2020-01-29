package com.couchmate.data.schema

import java.time.{LocalDateTime, OffsetDateTime}

import PgProfile.api._
import com.couchmate.data.models.{Airing, ListingCache}
import slick.lifted.{AppliedCompiledFunction, Tag}
import slick.migration.api.TableMigration
import slick.sql.SqlStreamingAction

import scala.concurrent.{ExecutionContext, Future}

class ListingCacheDAO(tag: Tag) extends Table[ListingCache](tag, "listing_cache") {
  def listingCacheId: Rep[Long] = column[Long]("listing_cache_id", O.PrimaryKey, O.AutoInc)
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id")
  def startTime: Rep[LocalDateTime] = column[LocalDateTime]("start_time", O.SqlType("timestamptz"))
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
    ProviderChannelDAO.providerChannelTable,
  )(
    _.providerChannelId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object ListingCacheDAO {
  val listingCacheTable = TableQuery[ListingCacheDAO]

  val init = TableMigration(listingCacheTable)
    .create
    .addColumns(
      _.listingCacheId,
      _.providerChannelId,
      _.startTime,
      _.airings,
    ).addForeignKeys(
      _.providerChannelFk,
    )

  private[this] lazy val getListingCacheCompiled = Compiled { (providerChannelId: Rep[Long], startTime: Rep[LocalDateTime]) =>
    listingCacheTable.filter { lc =>
      lc.providerChannelId === providerChannelId &&
      lc.startTime === startTime
    }
  }

  def getListingCache(
    providerChannelId: Long,
    startTime: LocalDateTime,
  ): AppliedCompiledFunction[(Long, LocalDateTime), Query[ListingCacheDAO, ListingCache, Seq], Seq[ListingCache]] = {
    getListingCacheCompiled(providerChannelId, startTime)
  }

  def upsertListingCache(lc: ListingCache): SqlStreamingAction[Vector[ListingCache], ListingCache, Effect] = {
    sql"""
         INSERT INTO listing_cache
         (listing_cache_id, provider_channel_id, start_time, airings)
         VALUES
         (${lc.listingCacheId}, ${lc.providerChannelId}, ${lc.startTime}, ${lc.airings})
         ON CONFLICT (listing_cache_id)
         DO UPDATE SET
            provider_channel_id = ${lc.providerChannelId},
            start_time = ${lc.startTime},
            airings = ${lc.airings}
         RETURNING *
       """.as[ListingCache]
  }
}
