package com.couchmate.data.db.query

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ProviderChannelTable
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
