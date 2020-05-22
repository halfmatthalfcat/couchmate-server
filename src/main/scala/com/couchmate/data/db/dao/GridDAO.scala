package com.couchmate.data.db.dao

import java.time.LocalDateTime

import com.couchmate.api.models.grid.{Grid, GridAiring}
import com.couchmate.data.db.PgProfile.plainAPI._
import com.couchmate.data.models.{RoomActivityType, RoomStatusType}
import com.couchmate.util.DateUtils

import scala.concurrent.{ExecutionContext, Future}

class GridDAO {

  def getGrid(
    providerId: Long,
    startTime: LocalDateTime,
    duration: Int,
  ): Future[Grid] = {
    val endTime: LocalDateTime = startTime.plusMinutes(duration)
    db.run(GridDAO.getGrid(
      providerId,
      DateUtils.roundNearestHour(startTime),
      endTime,
    )) map { airings: Seq[GridAiring] =>
      Grid(
        providerId,
        startTime,
        endTime,
        duration,
        airings,
      )
    }
  }

}

object GridDAO {
  private[dao] def getGrid(
    providerId: Long,
    startTime: LocalDateTime,
    endTime: LocalDateTime,
  ) = {
    sql"""SELECT            a.airing_id, a.start_time, a.end_time, a.duration,
                            pc.provider_channel_id, pc.channel, c.callsign,
                            s.title, s.description, s.type,
                            se.series_name,
                            spe.sport_event_title,
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
            (a.start_time >= $startTime AND a.start_time <= $endTime) OR
            (a.end_time > $startTime AND a.end_time <= $endTime) OR
            (a.start_time <= $startTime AND a.end_time > $startTime)
          ) AND l.active = true
          ORDER BY          c, a.start_time
        """.as[GridAiring]
  }
}
