package com.couchmate.db.dao

import java.time.LocalDateTime

import com.couchmate.common.models.ListingCache
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.query.LineupCacheQueries
import com.couchmate.db.table.ListingCacheTable

import scala.concurrent.{ExecutionContext, Future}

class ListingCacheDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends LineupCacheQueries {

  def getListingCache(providerCacheId: Long, startTime: LocalDateTime): Future[Option[ListingCache]] = {
    db.run(super.getListingCache(providerCacheId, startTime).result.headOption)
  }

  def upsertListingCache(listingCache: ListingCache): Future[ListingCache] =
    listingCache.listingCacheId.fold(
      db.run((ListingCacheTable.table returning ListingCacheTable.table) += listingCache)
    ) { (listingCacheId: Long) => db.run(for {
      _ <- ListingCacheTable.table.update(listingCache)
      updated <- super.getListingCache(listingCacheId, listingCache.startTime)
    } yield updated.result.head.transactionally)}

}
