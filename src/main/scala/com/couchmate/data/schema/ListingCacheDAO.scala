package com.couchmate.data.schema

import java.time.OffsetDateTime

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.{Airing, ListingCache}
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class ListingCacheDAO(tag: Tag) extends Table[ListingCache](tag, "listing_cache") {
  def listingCacheId: Rep[Long] = column[Long]("listing_cache_id", O.PrimaryKey, O.AutoInc)
  def providerChannelId: Rep[Long] = column[Long]("provider_channel_id")
  def startTime: Rep[OffsetDateTime] = column[OffsetDateTime]("start_time", O.SqlType("timestamptz"))
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

  def getListingCache(
    providerChannelId: Long,
    startTime: OffsetDateTime,
  )(
    implicit
    db: Database
  ): Future[Option[ListingCache]] = {
    db.run(listingCacheTable.filter { lc =>
      lc.providerChannelId === providerChannelId &&
      lc.startTime === startTime
    }.result.headOption)
  }

  def upsertListingCache(listingCache: ListingCache)(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[ListingCache, ListingCache, NotUsed] = Slick.flowWithPassThrough {
    case listingCache @ ListingCache(None, _, _, _) =>
      (listingCacheTable returning listingCacheTable) += listingCache
    case listingCache @ ListingCache(Some(listingCacheId), _, _, _) => for {
      _ <- listingCacheTable.filter(_.listingCacheId === listingCacheId).update(listingCache)
      lc <- listingCacheTable.filter(_.listingCacheId === listingCacheId).result.head
    } yield lc
  }
}
