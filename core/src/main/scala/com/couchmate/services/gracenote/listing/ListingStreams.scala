package com.couchmate.services.gracenote.listing

import java.time.LocalDateTime
import akka.NotUsed
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{RestartSource, Source}
import com.couchmate.common.dao.{EpisodeDAO, LineupDAO, ListingCacheDAO, ProviderChannelDAO, ProviderDAO, ShowDAO, SportEventDAO, SportEventTeamDAO, SportOrganizationTeamDAO, SportTeamDAO, UserNotificationQueueDAO, UserNotificationSeriesDAO, UserNotificationShowDAO, UserNotificationTeamDAO}
import com.couchmate.common.models.thirdparty.gracenote
import com.couchmate.common.models.thirdparty.gracenote.{GracenoteAiring, GracenoteAiringPlan, GracenoteChannelAiring, GracenoteProgram, GracenoteSlotAiring, GracenoteSport}
import com.couchmate.common.util.DateUtils
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Airing, Channel, ChannelOwner, Episode, Lineup, Series, Show, ShowType, SportEvent, SportOrganization, SportTeam}
import com.couchmate.services.gracenote._
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object ListingStreams extends PlayJsonSupport {

  def slots(pullType: ListingPullType, startTime: LocalDateTime): Source[LocalDateTime, NotUsed] =
    Source
      .fromIterator(() => Range(0, pullType.value).map(i => DateUtils.roundNearestHour(
        startTime.plusHours(i)
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
    db: Database,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
    ctx: ActorContext[_]
  ): Source[GracenoteAiringPlan, NotUsed] = {
    val channelOwner: Option[ChannelOwner] = channelAiring
      .affiliateId
      .map(id => ChannelOwner(
        channelOwnerId = None,
        extId = id,
        callsign = channelAiring.affiliateCallSign.getOrElse("N/A")
      ))

    Source
      .future(ProviderChannelDAO.addOrGetChannel(
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
      .mapAsync(10)(ListingCacheDAO.upsertListingCacheWithDiff(
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
    db: Database,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
    config: Config,
    ctx: ActorContext[_]
  ): Source[Lineup, NotUsed] = RestartSource.onFailuresWithBackoff(
    minBackoff = 1.seconds,
    maxBackoff = 1.seconds,
    randomFactor = 0,
    maxRestarts = 3
  )(() => Source.future(gracenoteAiring match {
    case GracenoteAiring(_, _, _, _, program) if program.sportsId.nonEmpty =>
      sport(program, sports).recoverWith {
        case ex: Throwable =>
          System.out.println(s"Sport failed: ${ex.getMessage}")
          Future.failed(ex)
      }
    case GracenoteAiring(_, _, _, _, program) if (
      program.seriesId.nonEmpty &&
      program.sportsId.isEmpty
    ) =>
      episode(program).recoverWith {
        case ex: Throwable =>
          System.out.println(s"Episode failed: ${ex.getMessage}")
          Future.failed(ex)
      }
    case GracenoteAiring(_, _, _, _, program) => ShowDAO.addOrGetShow(Show(
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
    )).recoverWith {
      case ex: Throwable =>
        System.out.println(s"Show failed: ${ex.getMessage}")
        Future.failed(ex)
    }
  })).mapAsync(10)(show => for {
    l <- LineupDAO.addOrGetLineupFromProviderChannelAndAiring(
      providerChannelId,
      Airing(
        airingId = Airing.generateShortcode,
        showId = show.showId.get,
        startTime = gracenoteAiring.startTime,
        endTime = gracenoteAiring.endTime,
        duration = gracenoteAiring.duration,
        isNew = gracenoteAiring.isNew
      )
    )
    n <- {
      if (show.sportEventId.nonEmpty) {
        UserNotificationQueueDAO.addUserNotificationsForSport(
          l.airingId,
          providerChannelId,
          show.sportEventId.get
        )
      } else if (show.episodeId.nonEmpty) {
        UserNotificationQueueDAO.addUserNotificationsForEpisode(
          l.airingId,
          show.episodeId.get,
          providerChannelId,
        )
      } else {
        UserNotificationQueueDAO.addUserNotificationsForShow(
          l.airingId,
          providerChannelId,
        )
      }
    }
  } yield l)

  def disable(
    providerChannelId: Long,
    gracenoteAiring: GracenoteAiring,
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Source[Option[Lineup], NotUsed] =
    Source.future(LineupDAO.disableLineup(providerChannelId, gracenoteAiring))

  private[this] def episode(program: GracenoteProgram)(
    implicit
    ec: ExecutionContext,
    db: Database,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Show] = EpisodeDAO.addOrGetShow(
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
      season = program.seasonNum.getOrElse(0L),
      episode = program.episodeNum.getOrElse(0L)
    )
  )

  private[this] def sport(
    program: GracenoteProgram,
    sports: Seq[GracenoteSport]
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
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
          orgName = sportOrg.organizationName
        )).getOrElse(SportOrganization(
        sportOrganizationId = None,
        extSportId = program.sportsId.get,
        sportName = gnSport.map(_.sportsName).getOrElse("N/A"),
        orgName = None
      ))

    val teams: Seq[SportTeam] =
      program.teams.map(teams => teams.map(team => SportTeam(
        sportTeamId = None,
        extSportTeamId = team.teamBrandId.toLong,
        name = team.name,
      ))).getOrElse(Seq.empty)

    val homeId: Option[Long] =
      program
        .teams
        .flatMap(_.find(_.isHome.getOrElse(false)))
        .map(_.teamBrandId.toLong)

    val series: Option[Series] = program.seriesId.map(seriesId => Series(
      seriesId = None,
      extId = seriesId,
      seriesName = program.title,
      totalSeasons = None,
      totalEpisodes = None
    ))

    val episode = series.map(_ => Episode(
      episodeId = None,
      seriesId = None,
      season = program.seasonNum.getOrElse(0L),
      episode = program.episodeNum.getOrElse(0L)
    ))

    SportEventDAO.getOrAddShow(
      Show(
        showId = None,
        extId = program.rootId,
        `type` = ShowType.Sport,
        episodeId = None,
        sportEventId = None,
        title = program.eventTitle.getOrElse(program.title),
        description = program
          .shortDescription
          .orElse(program.longDescription)
          .getOrElse("N/A"),
        originalAirDate = program.origAirDate
      ),
      series,
      episode,
      org,
      event,
      teams,
      homeId,
    )
  }

}
