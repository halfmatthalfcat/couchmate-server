package com.couchmate.data.db.query

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.ShowTable

trait ShowQueries {

  private[db] lazy val getShow = Compiled { (showId: Rep[Long]) =>
    ShowTable.table.filter(_.showId === showId)
  }

  private[db] lazy val getShowByExt = Compiled { (extId: Rep[Long]) =>
    ShowTable.table.filter(_.extId === extId)
  }

}
