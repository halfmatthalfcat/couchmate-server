package com.couchmate.common.dao

import java.time.LocalDateTime

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.ListingCache
import com.couchmate.common.models.thirdparty.gracenote.{GracenoteAiring, GracenoteAiringPlan}
import com.couchmate.common.tables.ListingCacheTable

import scala.concurrent.{ExecutionContext, Future}

trait ListingCacheDAO {

  def getListingCache(
    providerChannelId: Long,
    startTime: LocalDateTime
  )(
    implicit
    db: Database
  ): Future[Option[ListingCache]] =
    db.run(ListingCacheDAO.getListingCache(providerChannelId, startTime))

  def getListingCache$()(
    implicit
    session: SlickSession
  ): Flow[(Long, LocalDateTime), Option[ListingCache], NotUsed] =
    Slick.flowWithPassThrough(
      (ListingCacheDAO.getListingCache _).tupled
    )

  def upsertListingCache(listingCache: ListingCache)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[ListingCache] =
    db.run(ListingCacheDAO.upsertListingCache(listingCache))

  def upsertListingCache$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[ListingCache, ListingCache, NotUsed] =
    Slick.flowWithPassThrough(ListingCacheDAO.upsertListingCache)

  def upsertListingCacheWithDiff(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[GracenoteAiringPlan] =
    db.run(ListingCacheDAO.upsertListingCacheWithDiff(
      providerChannelId,
      startTime,
      airings
    ))
}

object ListingCacheDAO {
  private[this] lazy val getListingCacheQuery= Compiled {
    (providerChannelId: Rep[Long], startTime: Rep[LocalDateTime]) =>
      ListingCacheTable.table.filter { lc =>
        lc.providerChannelId === providerChannelId &&
        lc.startTime === startTime
      }
  }

  private[common] def getListingCache(
    providerCacheId: Long,
    startTime: LocalDateTime
  ): DBIO[Option[ListingCache]] =
    getListingCacheQuery(providerCacheId, startTime).result.headOption

  private[common] def upsertListingCache(listingCache: ListingCache)(
    implicit
    ec: ExecutionContext
  ): DBIO[ListingCache] =
    listingCache.listingCacheId.fold[DBIO[ListingCache]](
      (ListingCacheTable.table returning ListingCacheTable.table) += listingCache
    ) { (listingCacheId: Long) => for {
      _ <- ListingCacheTable
        .table
        .filter(_.listingCacheId === listingCacheId)
        .update(listingCache)
      updated <- ListingCacheDAO.getListingCache(listingCacheId, listingCache.startTime)
    } yield updated.get}

  private[common] def upsertListingCacheWithDiff(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[GracenoteAiringPlan] = for {
    cacheExists <- getListingCache(providerChannelId, startTime)
    cache = cacheExists.map(_.airings).getOrElse(Seq.empty)
    _ <- upsertListingCache(ListingCache(
      listingCacheId = None,
      providerChannelId = providerChannelId,
      startTime = startTime,
      airings = airings,
    ))
  } yield {
    // New airings that don't exist in the previous cache
    val add: Seq[GracenoteAiring] =
      airings.filterNot(airing => cache.exists(_.equals(airing)))

    // Airings that were cached but not in the latest pull
    val remove: Seq[GracenoteAiring] =
      cache.filterNot(airing => airings.exists(_.equals(airing)))

    // Airings that exist in both the cache and the new pull
    val skip: Seq[GracenoteAiring] =
      cache
        .filterNot(airing => remove.exists(_.equals(airing)))
        .filter(airing => airings.exists(_.equals(airing)))

    GracenoteAiringPlan(
      providerChannelId,
      startTime,
      add,
      remove,
      skip
    )
  }
}
