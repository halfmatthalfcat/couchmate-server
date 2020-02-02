package com.couchmate.data.db

class CMDatabase()(
  implicit
  val ctx: CMContext,
) {

  val airing = new AiringDAO()
  val channel = new ChannelDAO()
  val episode = new EpisodeDAO()
  val lineupCache = new LineupCacheDAO()
  val lineup = new LineupDAO()
  val providerChannel = new ProviderChannelDAO()
  val provider = new ProviderDAO()
  val providerOwner = new ProviderOwnerDAO()
  val roomActivity = new RoomActivityDAO()
  val series = new SeriesDAO()
  val show = new ShowDAO()
  val sportEvent = new SportEventDAO()
  val sportOrganization = new SportOrganizationDAO()
  val userActivity = new UserActivityDAO()
  val user = new UserDAO()
  val userExt = new UserExtDAO()
  val userMeta = new UserMetaDAO()
  val userPrivate = new UserPrivateDAO()
  val userProvider = new UserProviderDAO()
  val zipProvider = new ZipProviderDAO()

}
