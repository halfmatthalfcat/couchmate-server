package com.couchmate.data.db.query

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{LineupTable, ProviderChannelTable}
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
