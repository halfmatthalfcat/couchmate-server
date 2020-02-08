package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.ShowTable

trait ShowQueries {

  private[db] lazy val getShow = Compiled { (showId: Rep[Long]) =>
    ShowTable.table.filter(_.showId === showId)
  }

  private[db] lazy val getShowByExt = Compiled { (extId: Rep[Long]) =>
    ShowTable.table.filter(_.extId === extId)
  }

}
