package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.{LineupTable, ProviderChannelTable}
import slick.lifted.Compiled

trait ListingQueries {

  private[db] lazy val getLineup = Compiled { (lineupId: Rep[Long]) =>
    LineupTable.table.filter(_.lineupId === lineupId)
  }

  private[db] lazy val lineupsExistForProvider = Compiled { (providerId: Rep[Long]) =>
    for {
      l <- LineupTable.table
      pc <- ProviderChannelTable.table if (
        l.providerChannelId === pc.providerChannelId &&
        pc.providerId === providerId
      )
    } yield pc.providerId
  }

}
