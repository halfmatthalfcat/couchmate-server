package com.couchmate.data.db.services

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, Routers}
import akka.cluster.typed.Cluster
import com.couchmate.Server

class DataServices()(
  implicit
  ctx: ActorContext[Server.Command],
  cluster: Cluster
) {

  val airingService: ActorRef[AiringService.Command] = ctx.spawn(
    Routers.group(AiringService.Group),
    "airing-service-group"
  )

  val channelService: ActorRef[ChannelService.Command] = ctx.spawn(
    Routers.group(ChannelService.Group),
    "channel-service-group"
  )

  val episodeService: ActorRef[EpisodeService.Command] = ctx.spawn(
    Routers.group(EpisodeService.Group),
    "episode-service-group"
  )

  val gridService: ActorRef[GridService.Command] = ctx.spawn(
    Routers.group(GridService.Group),
    "episode-service-group"
  )

  val lineupService: ActorRef[LineupService.Command] = ctx.spawn(
    Routers.group(LineupService.Group),
    "lineup-service-group"
  )

  val listingCacheService: ActorRef[ListingCacheService.Command] = ctx.spawn(
    Routers.group(ListingCacheService.Group),
    "listing-cache-service-group"
  )

  val providerChannelService: ActorRef[ProviderChannelService.Command] = ctx.spawn(
    Routers.group(ProviderChannelService.Group),
    "provider-channel-service-group"
  )

  val providerOwnerService: ActorRef[ProviderOwnerService.Command] = ctx.spawn(
    Routers.group(ProviderOwnerService.Group),
    "provider-owner-service-group"
  )

  val providerService: ActorRef[ProviderService.Command] = ctx.spawn(
    Routers.group(ProviderService.Group),
    "provider-service-group"
  )

  val roomActivityService: ActorRef[RoomActivityService.Command] = ctx.spawn(
    Routers.group(RoomActivityService.Group),
    "room-activity-service-group"
  )

  val seriesService: ActorRef[SeriesService.Command] = ctx.spawn(
    Routers.group(SeriesService.Group),
    "series-service-group"
  )

  val showService: ActorRef[ShowService.Command] = ctx.spawn(
    Routers.group(ShowService.Group),
    "show-service-group"
  )

  val sportEventService: ActorRef[SportEventService.Command] = ctx.spawn(
    Routers.group(SportEventService.Group),
    "sport-event-service-group"
  )

  val sportOrganizationService: ActorRef[SportOrganizationService.Command] = ctx.spawn(
    Routers.group(SportOrganizationService.Group),
    "sport-organization-service-group"
  )

  val userActivityService: ActorRef[UserActivityService.Command] = ctx.spawn(
    Routers.group(UserActivityService.Group),
    "user-activity-service-group"
  )

  val userExtService: ActorRef[UserExtService.Command] = ctx.spawn(
    Routers.group(UserExtService.Group),
    "user-ext-service-group"
  )

  val userMetaService: ActorRef[UserMetaService.Command] = ctx.spawn(
    Routers.group(UserMetaService.Group),
    "user-meta-service-group"
  )

  val userPrivateService: ActorRef[UserPrivateService.Command] = ctx.spawn(
    Routers.group(UserPrivateService.Group),
    "user-private-service-group"
  )

  val userProviderService: ActorRef[UserProviderService.Command] = ctx.spawn(
    Routers.group(UserProviderService.Group),
    "user-provider-service-group"
  )

  val userService: ActorRef[UserService.Command] = ctx.spawn(
    Routers.group(UserService.Group),
    "user-service-group"
  )

  val zipProviderService: ActorRef[ZipProviderService.Command] = ctx.spawn(
    Routers.group(ZipProviderService.Group),
    "zip-provider-service-group"
  )

}
