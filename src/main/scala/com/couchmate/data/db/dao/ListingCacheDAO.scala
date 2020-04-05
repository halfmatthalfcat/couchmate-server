package com.couchmate.data.db.dao

import java.time.LocalDateTime

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ListingCacheTable
import com.couchmate.data.models.ListingCache
import com.couchmate.data.thirdparty.gracenote.GracenoteAiring
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

class ListingCacheDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getListingCache(providerCacheId: Long, startTime: LocalDateTime): Future[Option[ListingCache]] = {
    db.run(ListingCacheDAO.getListingCache(providerCacheId, startTime).result.headOption)
  }

  def upsertListingCache(listingCache: ListingCache): Future[ListingCache] = db.run(
    listingCache.listingCacheId.fold[DBIO[ListingCache]](
      (ListingCacheTable.table returning ListingCacheTable.table) += listingCache
    ) { (listingCacheId: Long) => for {
      _ <- ListingCacheTable.table.update(listingCache)
      updated <- ListingCacheDAO.getListingCache(listingCacheId, listingCache.startTime).result.head
    } yield updated}.transactionally
  )

  def getOrAddListingCache(
    providerChannelId: Long,
    startTime: LocalDateTime,
    airings: Seq[GracenoteAiring],
  ): Future[ListingCache] = db.run((for {
    exists <- ListingCacheDAO.getListingCache(
      providerChannelId,
      startTime,
    ).result.headOption
    cache <- exists.fold[DBIO[ListingCache]](
      (ListingCacheTable.table returning ListingCacheTable.table) += ListingCache(
        listingCacheId = None,
        providerChannelId = providerChannelId,
        startTime = startTime,
        airings = airings,
      )
    )(DBIO.successful)
  } yield cache).transactionally)

}

object ListingCacheDAO {
  private[dao] lazy val getListingCache = Compiled {
    (providerChannelId: Rep[Long], startTime: Rep[LocalDateTime]) =>
      ListingCacheTable.table.filter { lc =>
        lc.providerChannelId === providerChannelId &&
        lc.startTime === startTime
      }
  }
}
