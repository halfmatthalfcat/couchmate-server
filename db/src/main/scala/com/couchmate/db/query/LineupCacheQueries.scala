package com.couchmate.db.query

import java.time.LocalDateTime

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.ListingCacheTable
import slick.lifted.Compiled

trait LineupCacheQueries {

  private[db] lazy val getListingCache = Compiled {
    (providerChannelId: Rep[Long], startTime: Rep[LocalDateTime]) =>
      ListingCacheTable.table.filter { lc =>
        lc.providerChannelId === providerChannelId &&
        lc.startTime === startTime
      }
  }

}
