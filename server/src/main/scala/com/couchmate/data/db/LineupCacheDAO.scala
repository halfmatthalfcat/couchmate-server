package com.couchmate.data.db

import java.time.LocalDateTime

import com.couchmate.common.models.ListingCache

class LineupCacheDAO()(
  implicit val ctx: CMContext
) extends QuillPlayJson {
  import ctx._

  private[this] implicit val listingCacheInsertMeta =
    insertMeta[ListingCache](_.listingCacheId)

  def getListingCache(
    providerChannelId: Long,
    startTime: LocalDateTime,
  ) = quote {
    query[ListingCache]
      .filter { lc =>
        lc.providerChannelId == providerChannelId &&
        lc.startTime == startTime
      }
  }

  def upsertListingCache(listingCache: ListingCache) = quote {
    query[ListingCache]
      .insert(lift(listingCache))
      .onConflictUpdate(_.listingCacheId)(
        (from, to) => from.airings -> to.airings,
      ).returning(lc => lc)
  }

}
