package com.couchmate.data.db.dao

import java.time.LocalDateTime

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.LineupCacheQueries
import com.couchmate.data.db.table.ListingCacheTable
import com.couchmate.data.models.ListingCache

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
