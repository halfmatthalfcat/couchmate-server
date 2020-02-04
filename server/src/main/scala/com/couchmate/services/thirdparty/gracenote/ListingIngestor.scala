package com.couchmate.services.thirdparty.gracenote

import akka.NotUsed
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.stream.scaladsl.Source
import com.couchmate.common.models.{Airing, Channel, Provider, ProviderChannel, Show, SportEvent, SportOrganization}
import com.couchmate.data.db.CMDatabase
import com.couchmate.data.thirdparty.gracenote.{GracenoteChannelAiring, GracenoteProgram, GracenoteProgramType}

import scala.concurrent.{ExecutionContext, Future}

class ListingIngestor(
  gnService: GracenoteService,
  providerIngestor: ProviderIngestor,
  database: CMDatabase,
) {
  import database._

  private[this] def ingestSport(program: GracenoteProgram): Show = {
    sportOrganization.getSportOrganizationBySportAndOrg(
      program.sportsId.get,
      program.organizationId,
    ) match {
      case Some(sportOrg: SportOrganization)
    }
  }

  private[this] def ingestProgram(program: GracenoteProgram): Show = {
    show.getShowFromExt(program.rootId) match {
      case Some(show: Show) => show
      case None => if (program.isSport) {

      } else if (program.isSeries) {

      } else {
        show.upsertShow(Show(
          showId = None,
          extId = program.rootId,
          `type` = "show",
          episodeId = None,
          sportEventId = None,
          title = program.title,
          description = program
            .shortDescription
            .orElse(program.longDescription)
            .getOrElse("N/A"),
          originalAirDate = program.origAirDate
        ))
      }
    }
  }

  private[this] def ingestProviderChannel(
    channel: Channel,
    providerId: Long,
  )(implicit ec: ExecutionContext): Future[ProviderChannel] = Future {
    providerChannel.getProviderChannelForProviderAndChannel(
      providerId,
      channel.channelId.get,
    ) match {
      case None =>
        providerChannel.upsertProviderChannel(ProviderChannel(
          providerChannelId = None,
          providerId,
          channel.channelId.get,
          channel.callsign,
        ))
      case Some(providerChannel: ProviderChannel) => providerChannel
    }
  }

  private[this] def ingestChannel(
    channelAiring: GracenoteChannelAiring,
  )(implicit ec: ExecutionContext): Future[Channel] = Future {
    channel.getChannelForExt(channelAiring.stationId) match {
      case None =>
        channel.upsertChannel(Channel(
          channelId = None,
          channelAiring.stationId,
          channelAiring.affiliateCallSign
                       .getOrElse(channelAiring.callSign)
        ))
      case Some(channel: Channel) => channel
    }
  }

  private[this] def ingestChannelAiring(
    channelAiring: GracenoteChannelAiring,
    providerId: Long,
  )(implicit ec: ExecutionContext): Future[Seq[Listing]] = for {
    channel <- ingestChannel(channelAiring)
    providerChannel <- ingestProviderChannel(channel, providerId)
  }

  private[this] def getProviderAndAirings(
    extListingId: String,
  )(implicit ec: ExecutionContext): Future[Seq[(Long, GracenoteChannelAiring)]] = {
    Future(provider.getProviderForExtAndOwner(extListingId, None)) flatMap {
      case Some(Provider(Some(providerId), _, _, _, _, _)) =>
        gnService.getListing(extListingId) map { airings =>
          airings.map(providerId -> _)
        }
      case None => for {
        gnProvider <- gnService.getProvider(extListingId)
        provider <- providerIngestor.ingestProvider(
          None,
          None,
          gnProvider
        )
        airings <- gnService.getListing(extListingId)
      } yield airings.map(provider.providerId.get -> _)
    }
  }

  def ingestListings(
    extListingId: String,
  )(implicit ec: ExecutionContext): Source[Seq[Listing], NotUsed] =
    Source
      .future(getProviderAndAirings(extListingId))
      .mapConcat(identity)
      .mapAsync(1)(airing => ingestChannelAiring(airing._2, airing._1))
      .fold(Seq[Listing]())(_ :+ _)
}
//package com.couchmate.services.thirdparty.gracenote
//
//import akka.stream.scaladsl.{Sink, Source}
//import com.couchmate.common.models.{Airing, Channel, Provider, ProviderChannel, Show}
//
//import scala.concurrent.{ExecutionContext, Future}
//import com.couchmate.data.schema.PgProfile.api._
//import com.couchmate.data.schema.ShowDAO
//import com.couchmate.data.thirdparty.gracenote.{GracenoteChannelAiring, GracenoteProgram}
//import com.typesafe.scalalogging.LazyLogging
//
//object ListingIngestor
//  extends LazyLogging {
//
//  private[this] def ingestProgram(
//    program: GracenoteProgram,
//  )(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[Show] = {
//    ShowDAO.getShowFromExt(program.rootId) flatMap {
//      case Some(show) =>
//        Future.successful(show)
//      case None => program match {
//        case Grace
//      }
//    }
//  }
//
//  private[this] def ingestProviderChannel(
//    channel: Channel,
//    providerId: Long,
//  )(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[ProviderChannel] = {
//    ProviderChannelDAO.getProviderChannelForProviderAndChannel(
//      providerId,
//      channel.channelId.get,
//    ) flatMap {
//      case None =>
//        ProviderChannelDAO.upsertProviderChannel(ProviderChannel(
//          providerChannelId = None,
//          providerId,
//          channel.channelId.get,
//          channel.callsign,
//        ))
//      case Some(providerChannel) =>
//        Future.successful(providerChannel)
//    }
//  }
//
//  private[this] def ingestChannel(
//    channelAiring: GracenoteChannelAiring,
//  )(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[Channel] = {
//    ChannelDAO.getChannelForExt(channelAiring.stationId) flatMap {
//      case None =>
//        ChannelDAO.upsertChannel(Channel(
//          channelId = None,
//          channelAiring.stationId,
//          channelAiring.affiliateCallSign
//                       .getOrElse(channelAiring.callSign)
//        ))
//      case Some(channel) =>
//        Future.successful(channel)
//    }
//  }
//
//  private[this] def ingestChannelAiring(
//    channelAiring: GracenoteChannelAiring,
//    providerId: Long,
//  )(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//    gs: GracenoteService,
//  ): Future[Seq[Airing]] = {
//    for {
//      channel <- ingestChannel(channelAiring),
//      providerChannel <- ingestProviderChannel(channel, providerId)
//    }
//  }
//
//  def ingestListings(
//    extListingId: String,
//  )(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//    gs: GracenoteService,
//  ) = Source.future(
//    ProviderDAO.getProviderForExtAndOwner(extListingId, None),
//  ) flatMapConcat {
//    case Some(Provider(Some(providerId), _, _, _, _, _)) =>
//      gs
//        .getListing(extListingId)
//        .mapAsync(1)(
//          channelAiring => ingestChannelAiring(channelAiring, providerId)
//        )
//    case None => Source.empty
//  }
//
//}
