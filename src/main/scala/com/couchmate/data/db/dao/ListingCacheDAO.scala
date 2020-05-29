package com.couchmate.data.db.dao

import java.time.LocalDateTime

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ListingCacheTable
import com.couchmate.data.models.ListingCache
import slick.lifted.Compiled

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
      updated <- ListingCacheDAO.getListingCache(listingCacheId, listingCache.startTime)
    } yield updated.get}
}
