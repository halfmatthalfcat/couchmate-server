//package com.couchmate.services.thirdparty.gracenote
//
//import akka.NotUsed
//import akka.actor.typed.receptionist.Receptionist.Listing
//import akka.stream.scaladsl.Source
//import com.couchmate.common.models.{Episode, Provider, ProviderChannel, Series, Show, SportEvent, SportOrganization}
//import com.couchmate.data.db.CMDatabase
//import com.couchmate.data.models.{Channel, Episode, Provider, ProviderChannel, Series, Show, SportEvent}
//import com.couchmate.data.thirdparty.gracenote.{GracenoteChannelAiring, GracenoteProgram, GracenoteProgramType}
//
//import scala.concurrent.{ExecutionContext, Future}
//
//class ListingIngestor(
//  gnService: GracenoteService,
//  providerIngestor: ProviderIngestor,
//  database: CMDatabase,
//) {
//  import database._
//
//  private[this] def ingestSport(program: GracenoteProgram)(
//    implicit
//    ec: ExecutionContext,
//  ): Future[Show] = ctx.transaction(for {
//    sportOrgExists <- Future(sportOrganization.getSportOrganizationBySportAndOrg(
//      program.sportsId.get,
//      program.organizationId,
//    ))
//    sportOrg <- sportOrgExists.fold(for {
//      gnSO <- gnService.getSportOrganization(
//        program.sportsId.get,
//        program.organizationId,
//      )
//      so <- Future(sportOrganization.upsertSportOrganization(gnSO))
//    } yield so)(Future.successful)
//    sportEventExists <- Future(sportEvent.getSportEventFromNameAndOrg(
//      program.eventTitle.get,
//      sportOrg.sportOrganizationId.get,
//    ))
//    sportEvent <- sportEventExists.fold(Future(sportEvent.upsertSportEvent(SportEvent(
//      sportEventId = None,
//      sportEventTitle = program.eventTitle.get,
//      sportOrganizationId = sportOrg.sportOrganizationId.get,
//    ))))(Future.successful)
//    show <- Future(show.upsertShow(Show(
//      showId = None,
//      extId = program.rootId,
//      `type` = "sport",
//      episodeId = None,
//      sportEventId = sportEvent.sportEventId,
//      title = program.title,
//      description = program
//        .shortDescription
//        .orElse(program.longDescription)
//        .getOrElse("N/A"),
//      originalAirDate = program.origAirDate
//    )))
//  } yield show)
//
//  private[this] def ingestEpisode(program: GracenoteProgram)(
//    implicit
//    ec: ExecutionContext,
//  ): Future[Show] = ctx.transaction(for {
//    seriesExists <- Future(series.getSeriesByExt(program.seriesId.get))
//    series <- seriesExists.fold(Future(series.upsertSeries(Series(
//      seriesId = None,
//      seriesName = program.title,
//      extId = program.seriesId.get,
//      totalEpisodes = None,
//      totalSeasons = None,
//    ))))(Future.successful)
//    episode <- Future(episode.upsertEpisode(Episode(
//      episodeId = None,
//      seriesId = series.seriesId.get,
//      season = program.seasonNumber,
//      episode = program.episodeNumber,
//    )))
//    show <- Future(show.upsertShow(Show(
//      showId = None,
//      extId = program.rootId,
//      `type` = "episode",
//      episodeId = episode.episodeId,
//      sportEventId = None,
//      title = program.episodeTitle.getOrElse(program.title),
//      description = program
//        .shortDescription
//        .orElse(program.longDescription)
//        .getOrElse("N/A"),
//      originalAirDate = program.origAirDate,
//    )))
//  } yield show)
//
//  private[this] def ingestProgram(program: GracenoteProgram)(
//    implicit
//    ec: ExecutionContext,
//  ): Future[Show] = {
//    show.getShowFromExt(program.rootId) match {
//      case Some(show: Show) => Future.successful(show)
//      case None => if (program.isSport) {
//        ingestSport(program)
//      } else if (program.isSeries) {
//        ingestEpisode(program)
//      } else {
//        Future(show.upsertShow(Show(
//          showId = None,
//          extId = program.rootId,
//          `type` = "show",
//          episodeId = None,
//          sportEventId = None,
//          title = program.title,
//          description = program
//            .shortDescription
//            .orElse(program.longDescription)
//            .getOrElse("N/A"),
//          originalAirDate = program.origAirDate
//        )))
//      }
//    }
//  }
//
//  private[this] def ingestProviderChannel(
//    channel: Channel,
//    providerId: Long,
//  )(implicit ec: ExecutionContext): Future[ProviderChannel] = Future {
//    providerChannel.getProviderChannelForProviderAndChannel(
//      providerId,
//      channel.channelId.get,
//    ) match {
//      case None =>
//        providerChannel.upsertProviderChannel(ProviderChannel(
//          providerChannelId = None,
//          providerId,
//          channel.channelId.get,
//          channel.callsign,
//        ))
//      case Some(providerChannel: ProviderChannel) => providerChannel
//    }
//  }
//
//  private[this] def ingestChannel(
//    channelAiring: GracenoteChannelAiring,
//  )(implicit ec: ExecutionContext): Future[Channel] = Future {
//    channel.getChannelForExt(channelAiring.stationId) match {
//      case None =>
//        channel.upsertChannel(Channel(
//          channelId = None,
//          channelAiring.stationId,
//          channelAiring.affiliateCallSign
//                       .getOrElse(channelAiring.callSign)
//        ))
//      case Some(channel: Channel) => channel
//    }
//  }
//
//  private[this] def ingestChannelAiring(
//    channelAiring: GracenoteChannelAiring,
//    providerId: Long,
//  )(implicit ec: ExecutionContext): Source[Listing, NotUsed] =
//    Source.future(
//      for {
//        channel <- ingestChannel(channelAiring)
//        providerChannel <- ingestProviderChannel(channel, providerId)
//      } yield providerChannel
//    ).map { providerChannel =>
//      channelAiring.airings map { airing =>
//        providerChannel -> airing
//      }
//    }.mapConcat(identity)
//     .mapAsync(1) { case (providerChannel, airing) => ctx.transaction {
//       for {
//         show <- ingestProgram(airing.program)
//         airingExists <-
//       }
//    }}
//
//
//  private[this] def getProviderAndAirings(
//    extListingId: String,
//  )(implicit ec: ExecutionContext): Future[Seq[(Long, GracenoteChannelAiring)]] = {
//    Future(provider.getProviderForExtAndOwner(extListingId, None)) flatMap {
//      case Some(Provider(Some(providerId), _, _, _, _, _)) =>
//        gnService.getListing(extListingId) map { airings =>
//          airings.map(providerId -> _)
//        }
//      case None => for {
//        gnProvider <- gnService.getProvider(extListingId)
//        provider <- providerIngestor.ingestProvider(
//          None,
//          None,
//          gnProvider
//        )
//        airings <- gnService.getListing(extListingId)
//      } yield airings.map(provider.providerId.get -> _)
//    }
//  }
//
//  def ingestListings(
//    extListingId: String,
//  )(implicit ec: ExecutionContext): Source[Seq[Listing], NotUsed] =
//    Source
//      .future(getProviderAndAirings(extListingId))
//      .mapConcat(identity)
//      .mapAsync(1)(airing => ingestChannelAiring(airing._2, airing._1))
//      .fold(Seq[Listing]())(_ :+ _)
//}
