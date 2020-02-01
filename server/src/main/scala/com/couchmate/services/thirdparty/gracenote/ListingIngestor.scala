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
