package com.couchmate.data.db.dao

import java.time.LocalDateTime

import com.couchmate.api.models.grid.{Grid, GridAiring}
import com.couchmate.data.db.PgProfile.plainAPI._

import scala.concurrent.{ExecutionContext, Future}

class GridDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getGrid(
    providerId: Long,
    startTime: LocalDateTime,
    duration: Int,
  ): Future[Grid] = {
    val endTime: LocalDateTime = startTime.plusHours(duration)
    db.run(GridDAO.getGrid(
      providerId,
      startTime,
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
    endTime: LocalDateTime
  ) = {
    sql"""
          SELECT            a.airing_id, a.start_time, a.end_time, a.duration,
                            pc.channel, c.callsign,
                            s.title, s.description, s.type,
                            se.series_name,
                            spe.sport_event_title,
                            e.episode, e.season,
                            s.original_air_date
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
