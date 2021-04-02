package com.couchmate.common.dao

import java.time.LocalDateTime
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.ListingCache
import com.couchmate.common.models.thirdparty.gracenote.{GracenoteAiring, GracenoteAiringPlan}
import com.couchmate.common.tables.ListingCacheTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object ListingCacheDAO {
  private[this] lazy val getListingCacheQuery = Compiled {
    (listingCacheId: Rep[Long]) =>
      ListingCacheTable.table.filter(_.listingCacheId === listingCacheId)
  }

  def getListingCache(listingCacheId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[ListingCache] = cache(
    "getListingCache",
    listingCacheId
  )(db.run(getListingCacheQuery(listingCacheId).result.head))()

  private[this] lazy val getListingCacheForProviderAndStartQuery = Compiled {
    (providerChannelId: Rep[Long], startTime: Rep[LocalDateTime]) =>
      ListingCacheTable.table.filter { lc =>
        lc.providerChannelId === providerChannelId &&
        lc.startTime === startTime
      }
  }

  def getListingCacheForProviderAndStart(
    providerChannelId: Long,
    startTime: LocalDateTime
  )(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[ListingCache]] = cache(
    "getListingCacheForProviderAndStart",
    providerChannelId,
    startTime.toString
  )(db.run(getListingCacheForProviderAndStartQuery(
    providerChannelId,
    startTime
  ).result.headOption))(
    // Cache in redis for a week
    // Cache in mem for an hour
    ttl = Some(7.days),
    ratio = Some(7 * 24),
    bust = bust
  )

  private[this] def addListingCacheForId(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Long] = cache(
    "addListingCacheForId",
    providerChannelId,
    startTime.toString
  )(db.run(
    sql"""SELECT insert_or_get_listing_cache_id($providerChannelId, $startTime, $airings)"""
      .as[Long].head
  ))()

  def addOrGetListingCache(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[(Boolean, ListingCache)] = (for {
    exists <- getListingCacheForProviderAndStart(
      providerChannelId, startTime
    )(bust = bust)
    listingCache <- exists.fold(for {
      _ <- addListingCacheForId(providerChannelId, startTime, airings)
      lc <- getListingCacheForProviderAndStart(
        providerChannelId, startTime
      )(bust = true).map(_.get)
    } yield lc)(Future.successful)
  } yield (exists.nonEmpty, listingCache))

  /**
   * TODO: compare airing hashes and update listings based on changes
   */
  def upsertListingCacheWithDiff(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[GracenoteAiringPlan] = for {
    (exists, listingCache) <- addOrGetListingCache(
      providerChannelId, startTime, airings
    )()
    cache = if (!exists) Seq.empty else listingCache.airings
    add = airings.filterNot(airing => cache.exists(_.equals(airing)))
    remove = cache.filterNot(airing => airings.exists(_.equals(airing)))
    skip = cache
      .filterNot(airing => remove.exists(_.equals(airing)))
      .filter(airing => airings.exists(_.equals(airing)))
    _ <- if (exists && (add.nonEmpty || remove.nonEmpty)) { for {
      _ <- db.run((for {
        c <- ListingCacheTable.table.filter { lc =>
          lc.providerChannelId === providerChannelId &&
          lc.startTime === startTime
        }
      } yield c.airings).update(airings))
      _ <- getListingCacheForProviderAndStart(
        providerChannelId,
        startTime
      )(bust = true)
    } yield ()} else { Future.successful() }
  } yield GracenoteAiringPlan(
    providerChannelId,
    startTime,
    add,
    remove,
    skip
  )
}
