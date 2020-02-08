package com.couchmate.db

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.dao.{AiringDAO, ChannelDAO, EpisodeDAO, ListingCacheDAO}

import scala.concurrent.ExecutionContext

class CMDatabase(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  val airing: AiringDAO = new AiringDAO(db)
  val channel: ChannelDAO = new ChannelDAO(db)
  val episode: EpisodeDAO = new EpisodeDAO(db)
  val listingCache: ListingCacheDAO = new ListingCacheDAO(db)

}
