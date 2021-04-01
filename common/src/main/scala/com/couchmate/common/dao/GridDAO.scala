package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.api.grid._
import com.couchmate.common.util.DateUtils
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import java.time.{LocalDateTime, ZoneId}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object GridDAO {
  def getGrid(
    providerId: Long,
    pages: Int = 4,
  )(
    bust: Boolean = false,
    bustFn: String => Future[_] = _ => Future.successful()
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Grid] = {
    val now: LocalDateTime =
      DateUtils.roundNearestHour(LocalDateTime.now(ZoneId.of("UTC")))
    for {
      provider <- ProviderDAO.getProvider(providerId)
      pages <- Future.sequence(
        Seq
          .tabulate[LocalDateTime](pages)(p => now.plusHours(p))
          .map(startDate => getGridPage(
            providerId,
            startDate
          )(bust = bust, bustFn = bustFn))
      )
    } yield Grid(
      providerId,
      provider.get.name,
      now,
      pages,
    )
  }

  def getGridPage(
    providerId: Long,
    startDate: LocalDateTime
  )(
    bust: Boolean = false,
    bustFn: String => Future[_] = _ => Future.successful()
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[GridPage] = cache(
    "getGridPage",
    providerId,
    startDate.toString
  )(for {
    airings <- getGridRaw(providerId, startDate, startDate.plusHours(1))
    gridSeries <- SeriesDAO.getAllGridSeries
    gridSportTeams <- SportTeamDAO.getAllGridSportRows
    groupedGridSportTeams = gridSportTeams.groupBy(_.sportEventId)
    extendedAirings = airings.map(airing => airing.toExtended(
      series = airing.episodeId.flatMap(
        episodeId => gridSeries.find(_.episodeId == episodeId)
      ),
      sport = for {
        sportEventId <- airing.sportEventId
        gridSportTeams <- groupedGridSportTeams.get(sportEventId)
      } yield gridSportTeams.head.toGridSport.copy(
        teams = gridSportTeams.map(_.toGridSportTeam)
      )
    ))
  } yield extendedAirings.foldLeft(GridPage(startDate, List.empty)) { case (page, airing) =>
    val channel: GridChannel = page.channels.headOption.getOrElse(
      GridChannel(
        airing.channelId,
        airing.channel,
        airing.callsign,
        Seq.empty
      )
    )

    if (channel.channelId == airing.channelId && page.channels.nonEmpty) {
      page.copy(
        channels = channel.copy(
          airings = channel.airings :+ airing
        ) :: page.channels.tail
      )
    } else if (channel.channelId == airing.channelId) {
      page.copy(
        channels = channel.copy(
          airings = channel.airings :+ airing
        ) :: page.channels
      )
    } else {
      page.copy(
        channels = GridChannel(
          airing.channelId,
          airing.channel,
          airing.callsign,
          Seq(airing)
        ) :: page.channels
      )
    }
  })(bust = bust, bustFn = bustFn, ttl = Some(1.hour))

  def getGridDynamic(providerId: Long)(bust: Boolean = false)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[GridDynamic] = cache(
    "getGridDynamic",
    providerId
  )(for {
    grid <- getGrid(providerId)()
    airings = grid.getAiringIds
    count <- db.run(UserActivityDAO.getProviderUserCount(providerId).head)
    dynamic <- getGridDynamicForAirings(airings)
  } yield GridDynamic(
    providerId = providerId,
    userCount = count,
    airings = dynamic
  ))(bust = bust, ttl = Some(5.seconds))

  private[this] def getGridRaw(
    providerId: Long,
    startTime: LocalDateTime,
    endTime: LocalDateTime,
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[GridAiring]] = cache(
    "getGridRaw",
    providerId,
    startTime.toString,
    endTime.toString
  )(db.run(
    sql"""SELECT            a.airing_id, a.start_time, a.end_time, a.duration,
                            pc.provider_channel_id, pc.channel,
                            c.callsign,
                            s.title, s.description, s.type,
                            a.is_new,
                            spe.sport_event_id,
                            e.episode_id,
                            s.original_air_date
          -- Main joins
          FROM              provider as p
          JOIN              provider_channel as pc
          ON                p.provider_id = pc.provider_id
          JOIN              lineup as l
          ON                pc.provider_channel_id = l.provider_channel_id
          JOIN              channel as c
          ON                pc.channel_id = c.channel_id
          JOIN              airing as a
          ON                l.airing_id = a.airing_id
          JOIN              show as s
          ON                a.show_id = s.show_id
          LEFT OUTER JOIN   episode as e
          ON                s.episode_id = e.episode_id
          LEFT OUTER JOIN   sport_event as spe
          ON                s.sport_event_id = spe.sport_event_id
          WHERE             p.provider_id = $providerId AND (
            (a.start_time >= $startTime AND a.start_time < $endTime) OR
            (a.end_time > $startTime AND a.end_time <= $endTime) OR
            (a.start_time <= $startTime AND a.end_time > $startTime)
          ) AND l.active = true
          ORDER BY          c, a.start_time
        """.as[GridAiring]
  ))()

  private[this] def getGridDynamicForAirings(airingIds: Seq[String])(
    implicit
    db: Database
  ): Future[Seq[GridAiringDynamic]] = {
    if (airingIds.isEmpty) {
      Future.successful(Seq.empty[GridAiringDynamic])
    } else {
      db.run(
        sql"""
        SELECT a.airing_id,
        CASE
        WHEN    EXTRACT(EPOCH FROM(start_time) - TIMEZONE('utc', NOW())) / 60 <= 15 AND
                EXTRACT(EPOCH FROM(start_time) - TIMEZONE('utc', NOW())) / 60 > 0
        THEN    'pregame'
        WHEN    EXTRACT(EPOCH FROM(start_time) - TIMEZONE('utc', NOW())) / 60 <= 0 AND
                duration - (EXTRACT(EPOCH FROM(end_time) - TIMEZONE('utc', NOW())) / 60) <= duration
        THEN    'open'
        WHEN    EXTRACT(EPOCH FROM(end_time) - TIMEZONE('utc', NOW())) / 60 < 0 AND
                EXTRACT(EPOCH FROM(end_time) - TIMEZONE('utc', NOW())) / 60 >= -15
        THEN    'postgame'
        ELSE    'closed'
        END AS  status,
        coalesce(roomCount.count, 0) as count,
        coalesce(followCount.following, 0) as following
        FROM airing as a
        LEFT OUTER JOIN (
          SELECT  airing_id, count(*) as count
          FROM    room_activity as current
          JOIN    (
            SELECT    user_id, max(created) as created
            FROM      room_activity
            GROUP BY  user_id
          ) as latest
          ON        current.user_id = latest.user_id
          AND       current.created = latest.created
          WHERE     action = 'joined'
          GROUP BY  airing_id
        ) as roomCount
        ON roomCount.airing_id = a.airing_id
        -- Following
        LEFT OUTER JOIN (
          SELECT t.airing_id, count(*) as following
          FROM (
             SELECT      airing_id
             FROM        user_notification_queue
             GROUP BY    user_id, airing_id
           ) as t
          GROUP BY t.airing_id
        ) as followCount
        ON followCount.airing_id = a.airing_id
        WHERE a.airing_id IN (#${airingIds.map(a => s"'$a'").mkString(",")})
       """.as[GridAiringDynamic]
      )
    }
  }

}
