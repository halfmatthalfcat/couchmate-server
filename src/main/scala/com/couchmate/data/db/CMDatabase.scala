package com.couchmate.data.db

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao._

import scala.concurrent.ExecutionContext

class CMDatabase(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  val airing: AiringDAO = new AiringDAO(db)
  val channel: ChannelDAO = new ChannelDAO(db)
  val episode: EpisodeDAO = new EpisodeDAO(db)
  val listingCache: ListingCacheDAO = new ListingCacheDAO(db)
  val lineup: LineupDAO = new LineupDAO(db)
  val providerChannel: ProviderChannelDAO = new ProviderChannelDAO(db)
  val provider: ProviderDAO = new ProviderDAO(db)
  val providerOwner: ProviderOwnerDAO = new ProviderOwnerDAO(db)
  val roomActivity: RoomActivityDAO = new RoomActivityDAO(db)
  val series: SeriesDAO = new SeriesDAO(db)
  val show: ShowDAO = new ShowDAO(db)
  val sportEvent: SportEventDAO = new SportEventDAO(db)
  val sportOrganization: SportOrganizationDAO = new SportOrganizationDAO(db)
  val userActivity: UserActivityDAO = new UserActivityDAO(db)
  val user: UserDAO = new UserDAO(db)
  val userExt: UserExtDAO = new UserExtDAO(db)
  val userMeta: UserMetaDAO = new UserMetaDAO(db)
  val userPrivate: UserPrivateDAO = new UserPrivateDAO(db)
  val userProvider: UserProviderDAO = new UserProviderDAO(db)
  val zipProvider: ZipProviderDAO = new ZipProviderDAO(db)

}
