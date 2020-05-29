package com.couchmate.data.db.services

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, Routers}
import akka.cluster.typed.Cluster
import com.couchmate.Server

case class DataServices(
  airingService: ActorRef[AiringService.Command],
  channelService: ActorRef[ChannelService.Command],
  episodeService: ActorRef[EpisodeService.Command],
  gridService: ActorRef[GridService.Command],
  lineupService: ActorRef[LineupService.Command],
  listingCacheService: ActorRef[ListingCacheService.Command],
  providerChannelService: ActorRef[ProviderChannelService.Command],
  providerOwnerService: ActorRef[ProviderOwnerService.Command],
  providerService: ActorRef[ProviderService.Command],
  roomActivityService: ActorRef[RoomActivityService.Command],
  seriesService: ActorRef[SeriesService.Command],
  showService: ActorRef[ShowService.Command],
  sportEventService: ActorRef[SportEventService.Command],
  sportOrganizationService: ActorRef[SportOrganizationService.Command],
  userActivityService: ActorRef[UserActivityService.Command],
  userExtService: ActorRef[UserExtService.Command],
  userMetaService: ActorRef[UserMetaService.Command],
  userPrivateService: ActorRef[UserPrivateService.Command],
  userProviderService: ActorRef[UserProviderService.Command],
  userService: ActorRef[UserService.Command],
  zipProviderService: ActorRef[ZipProviderService.Command]
)

object DataServices {
  def apply()(
    implicit
    ctx: ActorContext[Server.Command],
    cluster: Cluster
  ): DataServices = new DataServices(
    airingService = ctx.spawn(
      Routers.group(AiringService.Group),
      "airing-service-group"
    ),
    channelService = ctx.spawn(
      Routers.group(ChannelService.Group),
      "channel-service-group"
    ),
    episodeService = ctx.spawn(
      Routers.group(EpisodeService.Group),
      "episode-service-group"
    ),
    gridService = ctx.spawn(
      Routers.group(GridService.Group),
      "episode-service-group"
    ),
    lineupService = ctx.spawn(
      Routers.group(LineupService.Group),
      "lineup-service-group"
    ),
    listingCacheService = ctx.spawn(
      Routers.group(ListingCacheService.Group),
      "listing-cache-service-group"
    ),
    providerChannelService = ctx.spawn(
      Routers.group(ProviderChannelService.Group),
      "provider-channel-service-group"
    ),
    providerOwnerService = ctx.spawn(
      Routers.group(ProviderOwnerService.Group),
      "provider-owner-service-group"
    ),
    providerService = ctx.spawn(
      Routers.group(ProviderService.Group),
      "provider-service-group"
    ),
    roomActivityService = ctx.spawn(
      Routers.group(RoomActivityService.Group),
      "room-activity-service-group"
    ),
    seriesService = ctx.spawn(
      Routers.group(SeriesService.Group),
      "series-service-group"
    ),
    showService = ctx.spawn(
      Routers.group(ShowService.Group),
      "show-service-group"
    ),
    sportEventService = ctx.spawn(
      Routers.group(SportEventService.Group),
      "sport-event-service-group"
    ),
    sportOrganizationService = ctx.spawn(
      Routers.group(SportOrganizationService.Group),
      "sport-organization-service-group"
    ),
    userActivityService = ctx.spawn(
      Routers.group(UserActivityService.Group),
      "user-activity-service-group"
    ),
    userExtService = ctx.spawn(
      Routers.group(UserExtService.Group),
      "user-ext-service-group"
    ),
    userMetaService = ctx.spawn(
      Routers.group(UserMetaService.Group),
      "user-meta-service-group"
    ),
    userPrivateService = ctx.spawn(
      Routers.group(UserPrivateService.Group),
      "user-private-service-group"
    ),
    userProviderService = ctx.spawn(
      Routers.group(UserProviderService.Group),
      "user-provider-service-group"
    ),
    userService = ctx.spawn(
      Routers.group(UserService.Group),
      "user-service-group"
    ),
    zipProviderService = ctx.spawn(
      Routers.group(ZipProviderService.Group),
      "zip-provider-service-group"
    )
  )
}
