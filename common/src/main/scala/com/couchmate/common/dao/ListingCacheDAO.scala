package com.couchmate.common.dao

import akka.actor.typed.scaladsl.ActorContext

import java.time.LocalDateTime
import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.ListingCache
import com.couchmate.common.models.thirdparty.gracenote.{GracenoteAiring, GracenoteAiringPlan}
import com.couchmate.common.tables.ListingCacheTable
import play.api.libs.json.Json

import scala.concurrent.{ExecutionContext, Future}

trait ListingCacheDAO {

  def getListingCache(listingCacheId: Long)(
    implicit
    db: Database
  ): Future[ListingCache] =
    db.run(ListingCacheDAO.getListingCache(listingCacheId))

  def getListingCacheForProviderAndStart(
    providerChannelId: Long,
    startTime: LocalDateTime
  )(
    implicit
    db: Database
  ): Future[Option[ListingCache]] =
    db.run(ListingCacheDAO.getListingCacheForProviderAndStart(providerChannelId, startTime))

  def upsertListingCacheWithDiff(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    ctx: ActorContext[_]
  ): Future[GracenoteAiringPlan] =
    db.run(ListingCacheDAO.upsertListingCacheWithDiff(
      providerChannelId,
      startTime,
      airings
    ))
}

object ListingCacheDAO {
  private[this] lazy val getListingCacheQuery = Compiled {
    (listingCacheId: Rep[Long]) =>
      ListingCacheTable.table.filter(_.listingCacheId === listingCacheId)
  }

  private[common] def getListingCache(listingCacheId: Long): DBIO[ListingCache] =
    getListingCacheQuery(listingCacheId).result.head

  private[this] lazy val getListingCacheForProviderAndStartQuery = Compiled {
    (providerChannelId: Rep[Long], startTime: Rep[LocalDateTime]) =>
      ListingCacheTable.table.filter { lc =>
        lc.providerChannelId === providerChannelId &&
        lc.startTime === startTime
      }
  }

  private[common] def getListingCacheForProviderAndStart(
    providerCacheId: Long,
    startTime: LocalDateTime
  ): DBIO[Option[ListingCache]] =
    getListingCacheForProviderAndStartQuery(providerCacheId, startTime).result.headOption

  private[this] def addListingCacheForId(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  ) = {
    sql"""
      WITH row AS (
        INSERT INTO listing_cache
        ("provider_channel_id", "start_time", "airings")
        VALUES
        ($providerChannelId, $startTime, $airings)
        ON CONFLICT ("provider_channel_id", "start_time")
        DO NOTHING
        RETURNING listing_cache_id
      ) SELECT listing_cache_id FROM row
        UNION SELECT listing_cache_id FROM listing_cache
        WHERE provider_channel_id = $providerChannelId AND
              start_time = $startTime
      """.as[Long]
  }

  private[common] def addAndGetListingCache(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(implicit ec: ExecutionContext): DBIO[(Boolean, ListingCache)] = (for {
    exists <- getListingCacheForProviderAndStartQuery(
      providerChannelId, startTime
    ).result.headOption.map(_.nonEmpty)
    listingCacheId <- addListingCacheForId(
      providerChannelId, startTime, airings
    ).head
    listingCache <- getListingCache(listingCacheId)
  } yield (exists, listingCache)).transactionally

  private[common] def upsertListingCacheWithDiff(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(
    implicit
    ec: ExecutionContext,
    ctx: ActorContext[_]
  ): DBIO[GracenoteAiringPlan] = for {
    (exists, listingCache) <- addAndGetListingCache(
      providerChannelId, startTime, airings
    )
    cache = if (!exists) Seq.empty else listingCache.airings
    add = airings.filterNot(airing => cache.exists(_.equals(airing)))
    remove = cache.filterNot(airing => airings.exists(_.equals(airing)))
    skip = cache
      .filterNot(airing => remove.exists(_.equals(airing)))
      .filter(airing => airings.exists(_.equals(airing)))
    _ = ctx.log.debug(s"Cached [$providerChannelId | ${startTime.toString}]: A ${add.size}, R ${remove.size}, S ${skip.size}")
    _ <- if (add.nonEmpty || remove.nonEmpty) {
      (for {
        c <- ListingCacheTable.table.filter { lc =>
          lc.providerChannelId === providerChannelId &&
          lc.startTime === startTime
        }
      } yield c.airings).update(airings)
    } else { DBIO.successful() }
  } yield GracenoteAiringPlan(
    providerChannelId,
    startTime,
    add,
    remove,
    skip
  )
}
