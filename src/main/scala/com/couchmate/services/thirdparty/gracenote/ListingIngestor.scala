package com.couchmate.services.thirdparty.gracenote

import akka.NotUsed
import akka.stream.scaladsl.Source
import com.couchmate.data.db.CMDatabase
import com.couchmate.data.models.{Provider, Show}
import com.couchmate.data.thirdparty.gracenote.GracenoteChannelAiring

import scala.concurrent.{ExecutionContext, Future}

case class GracenoteAiringDiff(
  add: Seq[GracenoteChannelAiring],
  remove: Seq[GracenoteChannelAiring],
)

class ListingIngestor(
  gnService: GracenoteService,
  providerIngestor: ProviderIngestor,
  database: CMDatabase,
) {
  import database._

  private[this] def ingestChannelAiring(
    channelAiring: GracenoteChannelAiring,
    providerId: Long,
  )(implicit ec: ExecutionContext): Source[Show, NotUsed] =
    Source.future(
      for {
        channel <- channel.getChannelFromGracenote(channelAiring)
        providerChannel <- providerChannel.getProviderChannelFromGracenote(
          channel,
          providerId,
        )
      } yield providerChannel
    ).map { providerChannel =>
      channelAiring.airings map { airing =>
        providerChannel -> airing
      }
    }.mapConcat(identity)
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


  private[this] def getProviderAndAirings(extListingId: String)(
    implicit
    ec: ExecutionContext,
  ): Future[Seq[(Long, GracenoteChannelAiring)]] = {
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
