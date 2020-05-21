package com.couchmate.external.gracenote.listing

import java.time.{LocalDateTime, ZoneId}

import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, Partition, Sink}
import akka.{Done, NotUsed}
import com.couchmate.data.db.CMDatabase
import com.couchmate.data.models.{Lineup, Provider, ProviderChannel}
import com.couchmate.external.gracenote.GracenoteService
import com.couchmate.external.gracenote.models.{GracenoteAiring, GracenoteChannelAiring}
import com.couchmate.external.gracenote.provider.ProviderIngestor
import com.couchmate.util.DateUtils
import com.couchmate.util.stream.CombineLatestWith
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

class ListingIngestor(
  gnService: GracenoteService,
  providerIngestor: ProviderIngestor,
  database: CMDatabase,
) extends LazyLogging {
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

  private[this] def LogAiringFlow(name: String): Flow[
    (ProviderChannel, GracenoteAiring),
    (ProviderChannel, GracenoteAiring),
    NotUsed,
  ] =
    Flow[(ProviderChannel, GracenoteAiring)]
      .log(name, { case ((pc, a)) =>
        s"${pc.providerChannelId.get}|${a.program.rootId}|${a.startTime.toString}|${a.endTime.toString}"
      })

  private[this] def RemoveLineup(
    implicit
    ec: ExecutionContext,
  ): Sink[(ProviderChannel, GracenoteAiring), Future[Done]] =
    Sink.foreachAsync(1)((lineup.disableFromGracenote _).tupled)

  private[this] def CollectAiring(implicit ec: ExecutionContext):
  Flow[
    Seq[GracenoteChannelAiring],
    (ProviderChannel, GracenoteAiring, Seq[GracenoteAiring], Seq[GracenoteAiring]),
    NotUsed,
  ] = Flow[Seq[GracenoteChannelAiring]]
    .mapConcat(identity)
    .mapAsync(1) { channelAiring => for {
        channel <- channel.getChannelFromGracenote(channelAiring)
        providerChannel <- providerChannel.getProviderChannelFromGracenote(
          channel,
          channelAiring,
        )
        cache <- listingCache.getOrAddListingCache(
          providerChannel.providerChannelId.get,
          channelAiring.startDate.get,
          channelAiring.airings,
        ).map(_.airings)
      // Combine the new airings and the cache, get distinct and pass them through the router
      } yield (channelAiring.airings ++ cache).distinctBy { airing =>
        s"${airing.program.rootId}${airing.startTime.toString}${airing.endTime.toString}"
      }.map { airing =>
        (providerChannel, airing, channelAiring.airings, cache)
      }
    }
    .mapConcat(identity)

  private[this] def airingExists(airing1: GracenoteAiring)(airing2: GracenoteAiring): Boolean = {
    (airing1.program.rootId == airing2.program.rootId) &&
    (airing1.startTime == airing2.startTime) &&
    (airing1.endTime == airing2.endTime)
  }

  private[this] val RouteAiring: Partition[(ProviderChannel, GracenoteAiring, Seq[GracenoteAiring], Seq[GracenoteAiring])] =
    Partition[(ProviderChannel, GracenoteAiring, Seq[GracenoteAiring], Seq[GracenoteAiring])](3, {
      case (_, airing, airings, cache) =>
        if (airings.exists(airingExists(airing)) && !cache.exists(airingExists(airing))) 0
        else if (!airings.exists(airingExists(airing)) && cache.exists(airingExists(airing))) 1
        else 2
    })

  private[this] def GetProvider(pullType: ListingPullType)(
    implicit
    ec: ExecutionContext,
  ): Flow[Long, Seq[GracenoteChannelAiring], NotUsed] =
    Flow[Long]
      .mapAsync(1) { providerId =>
        provider.getProvider(providerId) flatMap {
          case Some(Provider(Some(providerId), _, extListingId, _, _, _)) =>
            Future.successful((extListingId, providerId))
          case None =>
            Future.failed(new RuntimeException("Could not find provider for that providerId"))
        }
      }.mapConcat { case (extListingId, providerId) =>
        Seq.fill(pullType.value)((extListingId, providerId)).zipWithIndex
      }.foldAsync(Seq[GracenoteChannelAiring]()) { case (acc, ((extListingId, providerId), idx)) =>
        val startDate: LocalDateTime = DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC")).plusHours(idx)
        )
        gnService.getListing(extListingId, startDate).map(_.map(_.copy(providerId = Some(providerId))) ++ acc)
      }

  def GetTotalAirings: Flow[Seq[GracenoteChannelAiring], Int, NotUsed] =
    Flow[Seq[GracenoteChannelAiring]].map { airings =>
      airings.foldLeft(0) { case (acc, airing) =>
        acc + airing.airings.length
      }
    }

  def ingestListings(pullType: ListingPullType)(implicit ec: ExecutionContext): Flow[Long, Double, NotUsed] =
    Flow.fromGraph(GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._

      val idSource = builder.add(Flow[Long])
      val getProvider = builder.add(GetProvider(pullType))
      val broadcastAirings = builder.add(Broadcast[Seq[GracenoteChannelAiring]](2))

      val routeAiring = builder.add(RouteAiring)
      val collectAiring = builder.add(CollectAiring)
      val removeLineup = builder.add(RemoveLineup)
      val addLineup = builder.add(GetOrAddLineup)
      val getLineup = builder.add(GetOrAddLineup)
      val collectLineup = builder.add(Merge[Lineup](2))

      val totalAirings = builder.add(GetTotalAirings)
      val zipTotalAndAirings = builder.add(CombineLatestWith(
        (total: Int, curr: (Lineup, Long)) => {
          (curr._2.toDouble + 1) / total.toDouble
        }))

      val logAdds = builder.add(LogAiringFlow("add"))
      val logRemoves = builder.add(LogAiringFlow("remove"))
      val logPasses = builder.add(LogAiringFlow("pass"))

      // Pull airings from Gracenote
      idSource ~> getProvider ~> broadcastAirings

      // Process airings
      broadcastAirings.out(0) ~> collectAiring ~> routeAiring.in
      routeAiring.out(0).map(a => (a._1, a._2)) ~> logAdds ~> addLineup ~> collectLineup.in(0)
      routeAiring.out(1).map(a => (a._1, a._2)) ~> logRemoves ~> removeLineup
      routeAiring.out(2).map(a => (a._1, a._2)) ~> logPasses ~> getLineup ~> collectLineup.in(1)

      // Track job progress
      broadcastAirings.out(1) ~> totalAirings ~> zipTotalAndAirings.in0
      collectLineup.out.zipWithIndex ~> zipTotalAndAirings.in1

      FlowShape(idSource.in, zipTotalAndAirings.out)
    }).recover {
      case ex: Throwable =>
        logger.error(ex.toString);
        logger.error(ex.getStackTrace.mkString("\n"))
        100d
    }
}
