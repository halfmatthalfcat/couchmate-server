package com.couchmate.services.thirdparty.gracenote

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Broadcast, Flow, Partition, Sink, Source}
import com.couchmate.data.db.CMDatabase
import com.couchmate.data.db.dao.AiringDAO
import com.couchmate.data.models.{Provider, ProviderChannel, Show}
import com.couchmate.data.thirdparty.gracenote.{GracenoteAiring, GracenoteChannelAiring}

import scala.concurrent.{ExecutionContext, Future}

case class GracenoteAiringDiff(
  add: Seq[(ProviderChannel, GracenoteAiring)],
  remove: Seq[GracenoteAiring],
)

class ListingIngestor(
  gnService: GracenoteService,
  providerIngestor: ProviderIngestor,
  database: CMDatabase,
) {
  import database._

  private[this] def AddAiring(implicit ec: ExecutionContext): Flow[(ProviderChannel, GracenoteAiring), Show, NotUsed] =
    Flow[(ProviderChannel, GracenoteAiring)]
      .mapAsync(1) { case (pc, a) =>
        for {
          show <- show.getShowFromGracenoteProgram(
            a.program,
            gnService.getSportOrganization
          )
          airing <- airing.getAiringFromGracenote(
            show.showId.get,
            a,
          )
          _ <- lineup.getLineupFromGracenote(
            pc,
            airing,
          )
        } yield show
      }

  private[this] def RemoveAiring(
    implicit
    ec: ExecutionContext,
  ): Sink[(ProviderChannel, GracenoteAiring), Future[Done]] =
    Sink.foreachAsync(1)((lineup.disableFromGracenote _).tupled)

  private[this] def CollectAiring(
    channelAiring: GracenoteChannelAiring,
    providerId: Long,
  )(implicit ec: ExecutionContext):
  Flow[
    (Long, GracenoteChannelAiring),
    (ProviderChannel, GracenoteAiring, Seq[GracenoteAiring], Seq[GracenoteAiring]),
    NotUsed,
  ] = Flow[(Long, GracenoteChannelAiring)].mapAsync(1) {
    case (providerId, channelAiring) => for {
      channel <- channel.getChannelFromGracenote(channelAiring)
      providerChannel <- providerChannel.getProviderChannelFromGracenote(
        channel,
        providerId,
      )
      cache <- listingCache.getListingCache(
        providerChannel.providerChannelId.get,
        channelAiring.startDate.get,
      ).map(_.map(_.airings).getOrElse(Seq()))
    // Combine the new airings and the cache, get distinct and pass them through the router
    } yield (channelAiring.airings ++ cache).distinct.map { airing =>
      (providerChannel, airing, channelAiring.airings, cache)
    }
  }.mapConcat(identity)

  private[this] val RouteAiring: Partition[(ProviderChannel, GracenoteAiring, Seq[GracenoteAiring])] =
    Partition[(ProviderChannel, GracenoteAiring, Seq[GracenoteAiring], Seq[GracenoteAiring])](3, {
      case (providerChannel, airing, airings, cache) =>
        if ()
    })

//      .map { case (providerChannel, cache) =>
//      val add: Seq[(ProviderChannel, GracenoteAiring)] =
//        channelAiring
//          .airings
//          .filterNot(cache.contains)
//          .map { airing =>
//            providerChannel -> airing
//          }
//      val remove: Seq[GracenoteAiring] =
//        cache.filterNot(channelAiring.airings.contains)
//
//      GracenoteAiringDiff(
//        add,
//        remove,
//      )
//    }

  private[this] def GetProvider(implicit ec: ExecutionContext): Flow[String, (Long, GracenoteChannelAiring), NotUsed] =
    Flow[String].mapAsync(1) { extListingId =>
      provider.getProviderForExtAndOwner(extListingId, None) flatMap {
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
    }.mapConcat(identity)

  private[this] def getProviderAndAirings(extListingId: String)(
    implicit
    ec: ExecutionContext,
  ): Future[Seq[(Long, GracenoteChannelAiring)]] = {

  }

  def ingestListings(
    extListingId: String,
  )(implicit ec: ExecutionContext): Source[Seq[Show], NotUsed] =
    Source
      .future(getProviderAndAirings(extListingId))
      .mapConcat(identity)
      .flatMapConcat(airing => ingestChannelAiring(airing._2, airing._1))
      .fold(Seq[Show]())(_ :+ _)
}
