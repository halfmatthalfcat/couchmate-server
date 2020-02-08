package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.SeriesTable
import slick.lifted.Compiled

trait SeriesQueries {

  private[db] lazy val getSeries = Compiled { (seriesId: Rep[Long]) =>
    SeriesTable.table.filter(_.seriesId === seriesId)
  }

  private[db] lazy val getSeriesByExt = Compiled { (extId: Rep[Long]) =>
    SeriesTable.table.filter(_.extId === extId)
  }

}
