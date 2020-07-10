package com.couchmate.services.gracenote.listing

import java.time.{LocalDateTime, ZoneId}

import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{RestartSource, Source}
import com.couchmate.common.dao.{EpisodeDAO, LineupDAO, ListingCacheDAO, ProviderChannelDAO, ProviderDAO, ShowDAO, SportEventDAO}
import com.couchmate.common.models.thirdparty.gracenote
import com.couchmate.common.models.thirdparty.gracenote.{GracenoteAiring, GracenoteAiringPlan, GracenoteChannelAiring, GracenoteProgram, GracenoteSlotAiring, GracenoteSport}
import com.couchmate.common.util.DateUtils
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Airing, Channel, ChannelOwner, Episode, Lineup, Series, Show, ShowType, SportEvent, SportOrganization}
import com.couchmate.services.gracenote._
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object ListingStreams
  extends PlayJsonSupport
  with ProviderDAO
  with ProviderChannelDAO
  with ListingCacheDAO
  with ShowDAO
  with EpisodeDAO
  with SportEventDAO
  with LineupDAO {

  def slots(pullType: ListingPullType): Source[LocalDateTime, NotUsed] =
    Source
      .fromIterator(() => Range(0, pullType.value).map(i => DateUtils.roundNearestHour(
        LocalDateTime.now(ZoneId.of("UTC")).plusHours(i)
      )).iterator)

  def listings(
    extId: String,
    slot: LocalDateTime
  )(
    implicit
    ec: ExecutionContext,
    mat: Materializer,
    http: HttpExt,
    config: Config
  ): Source[GracenoteSlotAiring, NotUsed] = Source.future(for {
    response <- http.singleRequest(makeGracenoteRequest(
      config.getString("gracenote.host"),
      config.getString("gracenote.apiKey"),
      Seq("lineups", extId, "grid"),
      Map(
        "startDateTime" -> Some(slot.toString),
        "endDateTime" -> Some(
          slot.plusHours(1).toString
        )
      )
    ))
    decoded <- Gzip.decodeMessage(response).toStrict(10 seconds)
    channelAirings <- Unmarshal(decoded.entity).to[Seq[GracenoteChannelAiring]]
    _ = decoded.discardEntityBytes()
  } yield channelAirings).mapConcat(identity).map(gracenote.GracenoteSlotAiring(
    slot,
    _
  ))

  def channel(
    providerId: Long,
    channelAiring: GracenoteChannelAiring,
    startTime: LocalDateTime
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Source[GracenoteAiringPlan, NotUsed] = {
    val channelOwner: Option[ChannelOwner] = channelAiring
      .affiliateId
      .map(id => ChannelOwner(
        channelOwnerId = None,
        extId = id,
        callsign = channelAiring.affiliateCallSign.getOrElse("N/A")
      ))

    Source
      .future(getOrAddChannel(
        providerId,
        channelAiring.channel,
        channelOwner,
        Channel(
          channelId = None,
          channelAiring.stationId,
          channelOwnerId = None,
          callsign = channelAiring.callSign
        )
      ))
      .map(_.providerChannelId.get)
      .mapAsync(1)(upsertListingCacheWithDiff(
        _,
        startTime,
        channelAiring.airings
      ))
  }

  def lineup(
    providerChannelId: Long,
    gracenoteAiring: GracenoteAiring,
    sports: Seq[GracenoteSport]
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Source[Lineup, NotUsed] = RestartSource.onFailuresWithBackoff(
    minBackoff = 1.seconds,
    maxBackoff = 1.seconds,
    randomFactor = 0,
    maxRestarts = 3
  )(() => Source.future(gracenoteAiring match {
    case GracenoteAiring(_, _, _, program) if program.seriesId.nonEmpty =>
      episode(program)
    case GracenoteAiring(_, _, _, program) if program.sportsId.nonEmpty =>
      sport(program, sports)
    case GracenoteAiring(_, _, _, program) => getOrAddShow(Show(
      showId = None,
      extId = program.rootId,
      `type` = ShowType.Show,
      episodeId = None,
      sportEventId = None,
      title = program.episodeTitle.getOrElse(program.title),
      description = program
        .shortDescription
        .orElse(program.longDescription)
        .getOrElse("N/A"),
      originalAirDate = program.origAirDate,
    ))
  })).mapAsync(1)(show => getOrAddLineup(
    providerChannelId,
    show,
    Airing(
      airingId = None,
      showId = show.showId.get,
      startTime = gracenoteAiring.startTime,
      endTime = gracenoteAiring.endTime,
      duration = gracenoteAiring.duration
    )
  ))

  def disable(
    providerChannelId: Long,
    gracenoteAiring: GracenoteAiring,
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Source[Lineup, NotUsed] =
    Source.future(disableLineup(providerChannelId, gracenoteAiring))

  private[this] def episode(program: GracenoteProgram)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Show] = getOrAddEpisode(
    Show(
      showId = None,
      extId = program.rootId,
      `type` = ShowType.Episode,
      episodeId = None,
      sportEventId = None,
      title = program.episodeTitle.getOrElse(
        program.title
      ),
      description = program
        .shortDescription
        .orElse(program.longDescription)
        .getOrElse("N/A"),
      originalAirDate = program.origAirDate,
    ),
    Series(
      seriesId = None,
      extId = program.seriesId.get,
      seriesName = program.title,
      totalSeasons = None,
      totalEpisodes = None
    ),
    Episode(
      episodeId = None,
      seriesId = None,
      season = program.seasonNum,
      episode = program.episodeNum
    )
  )

  private[this] def sport(
    program: GracenoteProgram,
    sports: Seq[GracenoteSport]
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Show] = {
    val gnSport: Option[GracenoteSport] =
      sports.find(_.sportsId == program.sportsId.get)

    val event: SportEvent =
      SportEvent(
        sportEventTitle = program.eventTitle.getOrElse(
          program.title
        ),
        sportEventId = None,
        sportOrganizationId = None
      )

    val org: SportOrganization =
      program
        .organizationId
        .flatMap { orgId =>
          gnSport flatMap { gns =>
            gns.organizations.find(_.organizationId == orgId)
          }
        }
        .map(sportOrg => SportOrganization(
          sportOrganizationId = None,
          extSportId = program.sportsId.get,
          extOrgId = program.organizationId,
          sportName = gnSport.map(_.sportsName).getOrElse("N/A"),
          orgName = Some(sportOrg.organizationName)
        )).getOrElse(SportOrganization(
        sportOrganizationId = None,
        extSportId = program.sportsId.get,
        extOrgId = None,
        sportName = gnSport.map(_.sportsName).getOrElse("N/A"),
        orgName = None
      ))

    getOrAddSportEvent(
      Show(
        showId = None,
        extId = program.rootId,
        `type` = ShowType.Sport,
        episodeId = None,
        sportEventId = None,
        title = program.title,
        description = program
          .shortDescription
          .orElse(program.longDescription)
          .getOrElse("N/A"),
        originalAirDate = program.origAirDate
      ),
      org,
      event
    )
  }

}
