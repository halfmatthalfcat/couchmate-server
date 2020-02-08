package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.ChannelTable
import slick.lifted.Compiled

trait ChannelQueries {

  private[db] lazy val getChannel = Compiled { (channelId: Rep[Long]) =>
    ChannelTable.table.filter(_.channelId === channelId)
  }

  private[db] lazy val getChannelForExt = Compiled { (extId: Rep[Long]) =>
    ChannelTable.table.filter(_.extId === extId)
  }

}
