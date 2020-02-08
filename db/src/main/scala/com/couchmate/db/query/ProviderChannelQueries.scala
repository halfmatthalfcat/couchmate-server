package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.ProviderChannelTable
import slick.lifted.Compiled

trait ProviderChannelQueries {

  private[db] lazy val getProviderChannel = Compiled { (providerChannelId: Rep[Long]) =>
    ProviderChannelTable.table.filter(_.providerChannelId === providerChannelId)
  }

  private[db] lazy val getProviderChannelForProviderAndChannel = Compiled {
    (providerId: Rep[Long], channelId: Rep[Long]) =>
      ProviderChannelTable.table.filter { pc =>
        pc.providerId === providerId &&
        pc.channelId === channelId
      }
  }

}
