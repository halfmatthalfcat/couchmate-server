package com.couchmate.data.db.query

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.EpisodeTable\
import slick.lifted.Compiled

trait EpisodeQueries {

  private[db] lazy val getEpisode = Compiled { (episodeId: Rep[Long]) =>
    EpisodeTable.table.filter(_.episodeId === episodeId)
  }

}
