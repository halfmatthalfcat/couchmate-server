package com.couchmate.db.query

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.EpisodeTable\
import slick.lifted.Compiled

trait EpisodeQueries {

  private[db] lazy val getEpisode = Compiled { (episodeId: Rep[Long]) =>
    EpisodeTable.table.filter(_.episodeId === episodeId)
  }

}
