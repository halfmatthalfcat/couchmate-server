package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.api.grid.{GridSeries, GridSport}
import com.couchmate.common.models.data.{Airing, RoomActivity, RoomActivityAnalytics, RoomActivityType, Show, ShowType}
import com.couchmate.common.tables.RoomActivityTable
import com.couchmate.common.util.DateUtils
import play.api.libs.json.{Format, Json}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import java.time.{Duration, LocalDateTime, ZoneId}
import java.util.UUID
import scala.annotation.tailrec
import scala.compat.java8.DurationConverters
import scala.concurrent.{ExecutionContext, Future}

object RoomActivityAnalyticsDAO {
  private[this] case class RoomActivityAnalyticSession(
    begin: LocalDateTime,
    end: LocalDateTime,
    duration: Long,
    userId: UUID
  )

  case class RoomActivityAnalyticSessionTotal(
    userId: UUID,
    duration: Long,
    sessions: Int
  )

  object RoomActivityAnalyticSessionTotal {
    implicit val format: Format[RoomActivityAnalyticSessionTotal] = Json.format[RoomActivityAnalyticSessionTotal]
  }

  case class RoomActivityAnalyticAiring(
    airing: Airing,
    show: Show,
    sport: Option[GridSport],
    series: Option[GridSeries]
  )

  object RoomActivityAnalyticAiring {
    implicit val format: Format[RoomActivityAnalyticAiring] = Json.format[RoomActivityAnalyticAiring]
  }

  case class RoomActivityAnalyticSessions(
    airing: RoomActivityAnalyticAiring,
    sessions: Seq[RoomActivityAnalyticSessionTotal]
  )

  object RoomActivityAnalyticSessions {
    implicit val format: Format[RoomActivityAnalyticSessions] = Json.format[RoomActivityAnalyticSessions]
  }

  case class RoomActivityAnalyticContent(
    shows: Seq[RoomActivityAnalyticSessions],
    series: Seq[RoomActivityAnalyticSessions],
    sports: Seq[RoomActivityAnalyticSessions]
  )

  object RoomActivityAnalyticContent {
    implicit val format: Format[RoomActivityAnalyticContent] = Json.format[RoomActivityAnalyticContent]
  }

  private[this] def getRoomActivityAnalyticAiring(airingId: String)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[RoomActivityAnalyticAiring]] = (for {
    airing <- AiringDAO.getAiring(airingId)()
    show <- airing.fold(Future.successful(Option.empty[Show]))(
      show => ShowDAO.getShow(show.showId)
    )
    series <- show.flatMap(_.episodeId).fold(Future.successful(Option.empty[GridSeries]))(
      episodeId => SeriesDAO.getGridSeries(episodeId)
    )
    sport <- show.flatMap(_.sportEventId).fold(Future.successful(Option.empty[GridSport]))(
      sportEventId => SportTeamDAO.getGridSport(sportEventId)
    )
  } yield (airing, show, series, sport)) flatMap {
    case (Some(airing), Some(show), series, sport) => Future.successful(
      Some(RoomActivityAnalyticAiring(
        airing, show, sport, series
      ))
    )
    case _ => Future.successful(
      Option.empty[RoomActivityAnalyticAiring]
    )
  }

  private[this] lazy val getActivityRange = Compiled {
    (from: Rep[LocalDateTime], to: Rep[LocalDateTime]) =>
      RoomActivityTable.table.filter(rA =>
        rA.created between (from, to)
      ).sortBy(_.created.asc)
    }

  private[this] def getLast24Hours(
    implicit db: Database
  ): Future[Seq[RoomActivity]] = db.run(getActivityRange(
    DateUtils.roundNearestDay(
      LocalDateTime.now(ZoneId.of("UTC")).minusDays(1)
    ),
    DateUtils.roundNearestDay(
      LocalDateTime.now(ZoneId.of("UTC"))
    )
  ).result)


  private[this] def getLastWeek(
    implicit db: Database
  ): Future[Seq[RoomActivity]] = db.run(
    getActivityRange(
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC"))
                     .minusWeeks(1)
      ),
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC"))
      )
    ).result
  )


  private[this] def getLastMonth(
    implicit db: Database
  ): Future[Seq[RoomActivity]] = db.run(
    getActivityRange(
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC"))
                     .minusMonths(1)
      ),
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC"))
      )
    ).result
  )

  def getRoomAnalytics(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[RoomActivityAnalytics] = for {
    last24Hours <- getLast24Hours.flatMap(getAiringsFromActivities)
    lastWeek <- getLastWeek.flatMap(getAiringsFromActivities)
    lastMonth <- getLastMonth.flatMap(getAiringsFromActivities)
  } yield RoomActivityAnalytics(
    last24 = last24Hours,
    lastWeek = lastWeek,
    lastMonth = lastMonth
  )

  private[this] def getAiringsFromActivities(activities: Seq[RoomActivity])(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[RoomActivityAnalyticContent] = Future.sequence(
    activities.groupBy(_.airingId).map {
      case (airingId, roomActivities) =>
        getRoomActivityAnalyticAiring(airingId)
          .map(_.map(a => Seq(RoomActivityAnalyticSessions(
            airing = a,
            sessions = sumUserSessions(
              getRoomSessions(
                roomActivities,
                Seq.empty
              )
            )
          ))).getOrElse(Seq.empty))
    }.toSeq
  ).map(_.fold(Seq.empty)(_ ++ _)).map(sessions => {
    val typs = sessions.groupBy(_.airing.show.`type`)
    RoomActivityAnalyticContent(
      shows = typs
        .find(_._1 == ShowType.Show)
        .map(_._2)
        .getOrElse(Seq.empty)
        .sortBy(s => (s.sessions.size, s.sessions.map(_.duration).sum))
        .reverse,
      series = typs
        .find(_._1 == ShowType.Episode)
        .map(_._2)
        .getOrElse(Seq.empty)
        .sortBy(e => (e.sessions.size, e.sessions.map(_.duration).sum))
        .reverse,
      sports = typs
        .find(_._1 == ShowType.Sport)
        .map(_._2)
        .getOrElse(Seq.empty)
        .sortBy(s => (s.sessions.size, s.sessions.map(_.duration).sum))
        .reverse
    )
  })

  private[this] def sumUserSessions(sessions: Seq[RoomActivityAnalyticSession]): Seq[RoomActivityAnalyticSessionTotal] =
    sessions.groupBy(_.userId).map {
      case (userId, sessions) => RoomActivityAnalyticSessionTotal(
        userId = userId,
        duration = sessions.map(_.duration).sum,
        sessions = sessions.size
      )
    }.toSeq

  @tailrec
  private[this] def getRoomSessions(
    activities: Seq[RoomActivity],
    sessions: Seq[RoomActivityAnalyticSession]
  ): Seq[RoomActivityAnalyticSession] = {
    val start = activities.headOption match {
      case a @ Some(activity) if activity.action == RoomActivityType.Joined => a
      case _ => Option.empty
    }
    val endIdx = activities.indexWhere(_.action == RoomActivityType.Left)
    val end = if (endIdx == -1) Option.empty else Option(activities(endIdx))

    if (activities.tail.isEmpty) { sessions }
    else if (start.nonEmpty && end.nonEmpty) {
      getRoomSessions(
        activities.drop(endIdx),
        sessions :+ RoomActivityAnalyticSession(
          begin = start.get.created,
          end = end.get.created,
          duration = DurationConverters.toScala(
            Duration.between(start.get.created, end.get.created)
          ).toSeconds,
          userId = start.get.userId
        )
      )
    } else {
      getRoomSessions(
        activities.tail,
        sessions
      )
    }
  }

}
