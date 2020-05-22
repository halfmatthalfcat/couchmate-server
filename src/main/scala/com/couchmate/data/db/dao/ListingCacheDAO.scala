package com.couchmate.data.db.dao

import java.time.LocalDateTime

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ListingCacheTable
import com.couchmate.data.models.ListingCache
import com.couchmate.external.gracenote.models.GracenoteAiring
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

trait ListingCacheDAO {

  def getListingCache(
    providerCacheId: Long,
    startTime: LocalDateTime
  )(
    implicit
    db: Database
  ): Future[Option[ListingCache]] =
    db.run(ListingCacheDAO.getListingCache(providerCacheId, startTime))

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

  def getOrAddListingCache(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[ListingCache] =
    db.run(ListingCacheDAO.getOrAddListingCache(
      providerChannelId,
      startTime,
      airings
    ))

  def getOrAddListingCache$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[(Long, LocalDateTime, Seq[GracenoteAiring]), ListingCache, NotUsed] =
    Slick.flowWithPassThrough(
      (ListingCacheDAO.getOrAddListingCache _).tupled
    )

}

object ListingCacheDAO {
  private[this] lazy val getListingCacheQuery= Compiled {
    (providerChannelId: Rep[Long], startTime: Rep[LocalDateTime]) =>
      ListingCacheTable.table.filter { lc =>
        lc.providerChannelId === providerChannelId &&
        lc.startTime === startTime
      }
  }

  private[dao] def getListingCache(
    providerCacheId: Long,
    startTime: LocalDateTime
  ): DBIO[Option[ListingCache]] =
    getListingCacheQuery(providerCacheId, startTime).result.headOption

  private[dao] def upsertListingCache(listingCache: ListingCache)(
    implicit
    ec: ExecutionContext
  ): DBIO[ListingCache] =
    listingCache.listingCacheId.fold[DBIO[ListingCache]](
      (ListingCacheTable.table returning ListingCacheTable.table) += listingCache
    ) { (listingCacheId: Long) => for {
      _ <- ListingCacheTable.table.update(listingCache)
      updated <- ListingCacheDAO.getListingCache(listingCacheId, listingCache.startTime).result.head
    } yield updated}

  private[dao] def getOrAddListingCache(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring]
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[ListingCache] = for {
    exists <- ListingCacheDAO.getListingCache(
      providerChannelId,
      startTime,
    )
    cache <- exists.fold[DBIO[ListingCache]](
      (ListingCacheTable.table returning ListingCacheTable.table) += ListingCache(
        listingCacheId = None,
        providerChannelId = providerChannelId,
        startTime = startTime,
        airings = airings,
      )
    )(DBIO.successful)
  } yield cache
}
