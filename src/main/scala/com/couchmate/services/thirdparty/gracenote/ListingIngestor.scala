package com.couchmate.services.thirdparty.gracenote

import akka.stream.FlowShape
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Partition, Sink, Source}
import akka.{Done, NotUsed}
import com.couchmate.data.db.CMDatabase
import com.couchmate.data.models.{Lineup, Provider, ProviderChannel, Show}
import com.couchmate.data.thirdparty.gracenote.{GracenoteAiring, GracenoteChannelAiring}

import scala.concurrent.{ExecutionContext, Future}

class ListingIngestor(
  gnService: GracenoteService,
  providerIngestor: ProviderIngestor,
  database: CMDatabase,
) {
  import database._

  private[this] def GetOrAddLineup(implicit ec: ExecutionContext): Flow[(ProviderChannel, GracenoteAiring), Lineup, NotUsed] =
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
          lineup <- lineup.getLineupFromGracenote(
            pc,
            airing,
          )
        } yield lineup
      }

  private[this] def RemoveLineup(
    implicit
    ec: ExecutionContext,
  ): Sink[(ProviderChannel, GracenoteAiring), Future[Done]] =
    Sink.foreachAsync(1)((lineup.disableFromGracenote _).tupled)

  private[this] def CollectAiring(implicit ec: ExecutionContext):
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

  private[this] val RouteAiring: Partition[(ProviderChannel, GracenoteAiring, Seq[GracenoteAiring], Seq[GracenoteAiring])] =
    Partition[(ProviderChannel, GracenoteAiring, Seq[GracenoteAiring], Seq[GracenoteAiring])](3, {
      case (_, airing, airings, cache) =>
        if (airings.contains(airing) && !cache.contains(airing)) 0
        else if (!airings.contains(airing) && cache.contains(airing)) 1
        else 2
    })

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

  def ingestListings(implicit ec: ExecutionContext): Flow[String, Lineup, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val idSource = builder.add(Flow[String])
      val getProvider = builder.add(GetProvider)
      val routeAiring = builder.add(RouteAiring)
      val collectAiring = builder.add(CollectAiring)
      val removeLineup = builder.add(RemoveLineup)
      val addLineup = builder.add(GetOrAddLineup)
      val getLineup = builder.add(GetOrAddLineup)
      val collectLineup = builder.add(Merge[Lineup](2))

      idSource ~> getProvider ~> collectAiring ~> routeAiring.in

      routeAiring.out(0).map(a => (a._1, a._2)) ~> addLineup ~> collectLineup
      routeAiring.out(1).map(a => (a._1, a._2)) ~> removeLineup
      routeAiring.out(2).map(a => (a._1, a._2)) ~> getLineup ~> collectLineup

      FlowShape(idSource.in, collectLineup.out)
    })
}
