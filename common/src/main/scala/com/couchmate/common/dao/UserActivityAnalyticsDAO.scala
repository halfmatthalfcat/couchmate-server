package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{UserActivity, UserActivityType}
import com.couchmate.common.tables.UserActivityTable
import com.couchmate.common.util.DateUtils

import java.time.{Duration, LocalDateTime, ZoneId}
import java.util.UUID
import scala.annotation.tailrec
import scala.compat.java8.DurationConverters
import scala.concurrent.duration._

trait UserActivityAnalyticsDAO {
  private[this] case class UserAnalytics(
    // Daily Active Users (DAU)
    dau: Long,
    // Previous Day's DAU
    prevDau: Long,
    // Change in DAU
    dauChange: Double,
    // Weekly Active Users (WAU)
    // This is weekly point-in-time
    wau: Long,
    // Previous Week's WAU
    prevWau: Long,
    // Change in WAU
    wauChange: Double,
    // Rolling Weekly Active Users (RWAU)
    // This is 7 day rolling
    rwau: Long,
    // Previous Week's RWAU
    prevRwau: Long,
    // Change in RWAU
    rwauChange: Double,
    // Monthly Active Users (MAU)
    // This is monthly point-in-time
    mau: Long,
    // Previous Month's MAU
    prevMau: Long,
    // Change in MAU
    mauChange: Double,
    // Rolling Monthly Active Users (RMAU)
    // This is monthly rolling (number of days varies)
    rmau: Long,
    // Previous Month's RMAU
    prevRmau: Long,
    // Change in RMAU
    rmauChange: Long,
  )
  private[this] case class UserAnalyticSession(
    begin: LocalDateTime,
    end: LocalDateTime,
    duration: Long,
    model: Option[String],
    brand: Option[String],
    osVersion: Option[String],
    os: Option[String],
  )
  private[this] case class UserAnalyticSessions(
    userId: UUID,
    sessions: Seq[UserAnalyticSession]
  )

  private[this] lazy val getLast24HoursQuery = Compiled {
    UserActivityTable.table.filter(uA =>
      uA.created between(
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC")).minusDays(1)
        ),
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC"))
        )
      )
    ).sortBy(_.created.asc)
  }

  private[couchmate] def getLast24Hours: DBIO[Seq[UserActivity]] =
    getLast24HoursQuery.result

  private[this] lazy val getPrev24HoursQuery = Compiled {
    UserActivityTable.table.filter(uA =>
      uA.created between(
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC")).minusDays(2)
        ),
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC")).minusDays(1)
        )
      )
    ).sortBy(_.created.asc)
  }

  private[couchmate] def getPrev24Hours: DBIO[Seq[UserActivity]] =
    getPrev24HoursQuery.result

  private[this] lazy val getLast24HoursLastWeekQuery = Compiled {
    UserActivityTable.table.filter(uA =>
      uA.created between(
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC"))
                       .minusWeeks(1)
                       .minusDays(1)
        ),
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC")).minusWeeks(1)
        )
      )
    ).sortBy(_.created.asc)
  }

  private[couchmate] def getLast24HoursLastWeek: DBIO[Seq[UserActivity]] =
    getLast24HoursLastWeekQuery.result

  private[this] lazy val getLastWeekQuery = Compiled {
    UserActivityTable.table.filter(uA =>
      uA.created between(
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC")).minusWeeks(1)
        ),
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC"))
        )
      )
    ).sortBy(_.created.asc)
  }

  private[couchmate] def getLastWeek: DBIO[Seq[UserActivity]] =
    getLastWeekQuery.result

  private[this] lazy val getPrevWeekQuery = Compiled {
    UserActivityTable.table.filter(uA =>
      uA.created between(
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC")).minusWeeks(2)
        ),
        DateUtils.roundNearestHour(
          LocalDateTime.now(ZoneId.of("UTC")).minusWeeks(1)
        )
      )
    ).sortBy(_.created.asc)
  }

  private[couchmate] def getPrevWeek: DBIO[Seq[UserActivity]] =
    getPrevWeekQuery.result

  private[this] def getUserSessionsFromActivities(activities: Seq[UserActivity]): Seq[UserAnalyticSessions] = {
    activities
      .groupBy(_.userId)
      .map {
        case (userId, userActivities) => UserAnalyticSessions(
          userId = userId,
          sessions = getUserSessions(userActivities, Seq.empty)
        )
      }.toSeq
  }

  @tailrec
  private[this] def getUserSessions(activities: Seq[UserActivity], sessions: Seq[UserAnalyticSession]): Seq[UserAnalyticSession] = {
    val start = activities.headOption match {
      case a @ Some(activity) if activity.action == UserActivityType.Login => a
      case _ => Option.empty
    }
    val endIdx = activities.indexWhere(_.action == UserActivityType.Logout)
    val end = if (endIdx == -1) Option.empty else Option(activities(endIdx))

    if (activities.tail.isEmpty) { sessions }
    else if (start.nonEmpty && end.nonEmpty) {
      getUserSessions(
        activities.drop(endIdx),
        sessions :+ UserAnalyticSession(
          begin = start.get.created,
          end = end.get.created,
          duration = DurationConverters.toScala(
            Duration.between(start.get.created, end.get.created)
          ).toMinutes,
          model = start.get.model,
          brand = start.get.brand,
          osVersion = start.get.osVersion,
          os = start.get.os
        )
      )
    } else {
      getUserSessions(
        activities.tail,
        sessions
      )
    }
  }
}
