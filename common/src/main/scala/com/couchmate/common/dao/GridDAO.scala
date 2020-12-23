package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import java.time.{LocalDateTime, ZoneId}

import com.couchmate.common.dao.RoomActivityDAO.getUserLatestQuery
import com.couchmate.common.models.api.grid.{Grid, GridAiring, GridAiringExtended, GridChannel, GridPage, GridSportTeam}
import com.couchmate.common.models.data.{RoomActivityType, RoomStatusType}
import com.couchmate.common.tables.{AiringTable, ChannelTable, LineupTable, ProviderChannelTable, ProviderTable, RoomActivityTable, ShowTable}
import com.couchmate.common.util.DateUtils
import slick.sql.SqlStreamingAction

import scala.concurrent.{ExecutionContext, Future}

trait GridDAO {

  def getGrid(
    providerId: Long,
    pages: Int = 4,
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Grid] = {
    val now: LocalDateTime =
      DateUtils.roundNearestHour(LocalDateTime.now(ZoneId.of("UTC")))
    for {
      provider <- db.run(ProviderDAO.getProvider(providerId))
      pages <- Future.sequence(
        Seq
          .tabulate[LocalDateTime](pages)(p => now.plusHours(p))
          .map(startDate => db.run(GridDAO.getGridPage(
            providerId,
            startDate
          )))
      )
      count <- db.run(UserActivityDAO.getProviderUserCount(providerId).head)
    } yield Grid(
      providerId,
      provider.get.name,
      now,
      count,
      pages,
    )
  }

}

object GridDAO {
  private[common] def getGridPage(
    providerId: Long,
    startDate: LocalDateTime
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[GridPage] = for {
    airings <- getGrid(providerId, startDate, startDate.plusHours(1))
    extendedAirings <- DBIO.sequence(
      airings.map(airing => airing.sportEventId.fold[DBIO[GridAiringExtended]](DBIO.successful(airing.toExtended(Seq.empty))) {
        sportEventId => for {
          eventTeams <- SportEventTeamDAO.getSportEventTeams(sportEventId)
          teams <- DBIO.sequence(eventTeams.map(eT => SportTeamDAO.getSportTeam(eT.sportTeamId))).map(_.flatten)
        } yield airing.toExtended(
          teams.map(team => GridSportTeam(
            sportTeamId = team.sportTeamId.get,
            name = team.name,
            isHome = eventTeams.exists { eT =>
              team.sportTeamId.contains(eT.sportTeamId) &&
              eT.isHome
            }
          ))
        )
      })
    )
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
  }

  private[common] def getGrid(
    providerId: Long,
    startTime: LocalDateTime,
    endTime: LocalDateTime,
  ): SqlStreamingAction[Seq[GridAiring], GridAiring, Effect] = {
    sql"""SELECT            a.airing_id, a.start_time, a.end_time, a.duration,
                            pc.provider_channel_id, pc.channel, c.callsign,
                            s.title, s.description, s.type,
                            se.series_name,
                            spe.sport_event_id, spe.sport_event_title,
                            e.episode, e.season,
                            s.original_air_date,
                            roomStatus.status as status,
                            COALESCE(roomCount.count, 0) as count
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
          -- Room Count
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
            WHERE     action = ${RoomActivityType.Joined}
            GROUP BY  airing_id
          ) as roomCount
          ON roomCount.airing_id = a.airing_id
          -- Room Status
          JOIN (
            SELECT  airing_id, CASE
            WHEN    EXTRACT(EPOCH FROM(start_time) - TIMEZONE('utc', NOW())) / 60 <= 15 AND
                    EXTRACT(EPOCH FROM(start_time) - TIMEZONE('utc', NOW())) / 60 > 0
                    THEN ${RoomStatusType.PreGame}
            WHEN    EXTRACT(EPOCH FROM(start_time) - TIMEZONE('utc', NOW())) / 60 <= 0 AND
                    duration - (EXTRACT(EPOCH FROM(end_time) - TIMEZONE('utc', NOW())) / 60) <= duration
                    THEN ${RoomStatusType.Open}
            WHEN    EXTRACT(EPOCH FROM(end_time) - TIMEZONE('utc', NOW())) / 60 < 0 AND
                    EXTRACT(EPOCH FROM(end_time) - TIMEZONE('utc', NOW())) / 60 >= -15
                    THEN ${RoomStatusType.PostGame}
            ELSE    ${RoomStatusType.Closed}
            END AS  status
            FROM    airing
          ) as roomStatus
          ON roomStatus.airing_id = a.airing_id
          -- Optional Stuff
          LEFT OUTER JOIN   episode as e
          ON                s.episode_id = e.episode_id
          LEFT OUTER JOIN   series as se
          ON                e.series_id = se.series_id
          LEFT OUTER JOIN   sport_event as spe
          ON                s.sport_event_id = spe.sport_event_id
          LEFT OUTER JOIN   sport_organization as so
          ON                spe.sport_organization_id = so.sport_organization_id
          WHERE             p.provider_id = $providerId AND (
            (a.start_time >= $startTime AND a.start_time < $endTime) OR
            (a.end_time > $startTime AND a.end_time <= $endTime) OR
            (a.start_time <= $startTime AND a.end_time > $startTime)
          ) AND l.active = true
          ORDER BY          c, a.start_time
        """.as[GridAiring]
  }
}
