package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.plainAPI._
import com.couchmate.common.models.data.{UserActivity, UserActivityAnalytics, UserActivityType}
import com.couchmate.common.tables.{UserActivityAnalyticsTable, UserActivityTable}
import com.couchmate.common.util.DateUtils

import java.time.{Duration, LocalDateTime, ZoneId}
import java.util.UUID
import scala.annotation.tailrec
import scala.compat.java8.DurationConverters
import scala.concurrent.{ExecutionContext, Future}

trait UserActivityAnalyticsDAO {
  def getUserAnalytics(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserActivityAnalytics] =
    db.run(UserActivityAnalyticsDAO.getUserAnalytics)

  def getAndAddUserAnalytics(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserActivityAnalytics] =
    db.run(UserActivityAnalyticsDAO.getAndAddUserAnalytics)
}

object UserActivityAnalyticsDAO {
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

  private[this] lazy val getActivityRange = Compiled {
    (from: Rep[LocalDateTime], to: Rep[LocalDateTime]) =>
      UserActivityTable.table.filter(uA =>
        uA.created between(from, to)
      ).sortBy(_.created.asc)
    }

  private[couchmate] def getLast24Hours: DBIO[Seq[UserActivity]] =
    getActivityRange(
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC")).minusDays(1)
      ),
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC"))
      )
    ).result

  private[couchmate] def getPrev24Hours: DBIO[Seq[UserActivity]] =
    getActivityRange(
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC")).minusDays(2)
      ),
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC")).minusDays(1)
      )
    ).result

  private[couchmate] def getLast24HoursLastWeek: DBIO[Seq[UserActivity]] =
    getActivityRange(
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC"))
                     .minusWeeks(1)
                     .minusDays(1)
      ),
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC")).minusWeeks(1)
      )
    ).result

  private[couchmate] def getLastWeek: DBIO[Seq[UserActivity]] =
    getActivityRange(
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC")).minusWeeks(1)
      ),
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC"))
      )
    ).result

  private[couchmate] def getPrevWeek: DBIO[Seq[UserActivity]] =
    getActivityRange(
      DateUtils.roundNearestHour(
        LocalDateTime.now(ZoneId.of("UTC")).minusWeeks(2)
      ),
      DateUtils.roundNearestHour(
        LocalDateTime.now(ZoneId.of("UTC")).minusWeeks(1)
      )
    ).result

  private[couchmate] def getLastMonth: DBIO[Seq[UserActivity]] =
    getActivityRange(
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC")).minusMonths(1)
      ),
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC"))
      )
    ).result

  private[couchmate] def getPrevMonth: DBIO[Seq[UserActivity]] =
    getActivityRange(
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC")).minusMonths(2)
      ),
      DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC")).minusMonths(1)
      )
    ).result

  private[couchmate] def getUserAnalytics(implicit ec: ExecutionContext): DBIO[UserActivityAnalytics] = for {
    last24 <- getLast24Hours.map(getUserSessionsFromActivities)
    prev24 <- getPrev24Hours.map(getUserSessionsFromActivities)
    last24LastWeek <- getLast24HoursLastWeek.map(getUserSessionsFromActivities)
    lastWeek <- getLastWeek.map(getUserSessionsFromActivities)
    prevWeek <- getPrevWeek.map(getUserSessionsFromActivities)
    lastMonth <- getLastMonth.map(getUserSessionsFromActivities)
    prevMonth <- getPrevMonth.map(getUserSessionsFromActivities)
  } yield {
    val dauPerSessionMM = getPerSessionMM(last24)
    val dauTotalSessionMM = getTotalSessionMM(last24)
    val prevDauPerSessionMM = getPerSessionMM(prev24)
    val prevDauTotalSessionMM = getTotalSessionMM(prev24)
    val prevWeekDauPerSessionMM = getPerSessionMM(last24LastWeek)
    val prevWeekDauTotalSessionMM = getTotalSessionMM(last24LastWeek)
    val wauPerSessionMM = getPerSessionMM(lastWeek)
    val wauTotalSessionMM = getTotalSessionMM(lastWeek)
    val prevWauPerSessionMM = getPerSessionMM(prevWeek)
    val prevWauTotalSessionMM = getTotalSessionMM(prevWeek)
    val mauPerSessionMM = getPerSessionMM(lastMonth)
    val mauTotalSessionMM = getTotalSessionMM(lastMonth)
    val prevMauPerSessionMM = getPerSessionMM(prevMonth)
    val prevMauTotalSessionMM = getTotalSessionMM(prevMonth)
    UserActivityAnalytics(
      reportDate = DateUtils.roundNearestDay(
        LocalDateTime.now(ZoneId.of("UTC"))
      ).minusDays(1),
      dau = last24.size,
      prevDau = prev24.size,
      prevWeekDau = last24LastWeek.size,
      dauChange = getRelativeChange(last24.size.toDouble, prev24.size.toDouble),
      dauChangeLastWeek = getRelativeChange(last24.size.toDouble, last24LastWeek.size.toDouble),
      dauPerSessionMM = dauPerSessionMM,
      dauTotalSessionMM = dauTotalSessionMM,
      prevDauPerSessionMM = prevDauPerSessionMM,
      prevDauTotalSessionMM = prevDauTotalSessionMM,
      prevWeekDauPerSessionMM = prevWeekDauPerSessionMM,
      prevWeekDauTotalSessionMM = prevWeekDauTotalSessionMM,
      dauPerSessionMMChange = (
        getRelativeChange(dauPerSessionMM._1, prevDauPerSessionMM._1),
        getRelativeChange(dauPerSessionMM._2, prevDauPerSessionMM._2)
      ),
      dauTotalSessionMMChange = (
        getRelativeChange(dauTotalSessionMM._1, prevDauTotalSessionMM._1),
        getRelativeChange(dauTotalSessionMM._2, prevDauTotalSessionMM._2)
      ),
      dauPerSessionMMChangeLastWeek = (
        getRelativeChange(dauPerSessionMM._1, prevWeekDauPerSessionMM._1),
        getRelativeChange(dauPerSessionMM._2, prevWeekDauPerSessionMM._2)
      ),
      dauTotalSessionMMChangeLastWeek = (
        getRelativeChange(dauTotalSessionMM._1, prevWeekDauTotalSessionMM._1),
        getRelativeChange(dauTotalSessionMM._2, prevWeekDauTotalSessionMM._2)
      ),
      wau = lastWeek.size,
      prevWau = prevWeek.size,
      wauChange = getRelativeChange(lastWeek.size.toDouble, prevWeek.size.toDouble),
      wauPerSessionMM = wauPerSessionMM,
      wauTotalSessionMM = wauTotalSessionMM,
      prevWauPerSessionMM = prevWauPerSessionMM,
      prevWauTotalSessionMM = prevWauTotalSessionMM,
      wauPerSessionMMChange = (
        getRelativeChange(wauPerSessionMM._1, prevWauPerSessionMM._1),
        getRelativeChange(wauPerSessionMM._2, prevWauPerSessionMM._2)
      ),
      wauTotalSessionMMChange = (
        getRelativeChange(wauTotalSessionMM._1, prevWauTotalSessionMM._1),
        getRelativeChange(wauTotalSessionMM._2, prevWauTotalSessionMM._2)
      ),
      mau = lastMonth.size,
      prevMau = prevMonth.size,
      mauChange = getRelativeChange(lastMonth.size.toDouble, prevMonth.size.toDouble),
      mauPerSessionMM = mauPerSessionMM,
      mauTotalSessionMM = mauTotalSessionMM,
      prevMauPerSessionMM = prevMauPerSessionMM,
      prevMauTotalSessionMM = prevMauTotalSessionMM,
      mauPerSessionMMChange = (
        getRelativeChange(mauPerSessionMM._1, prevMauPerSessionMM._1),
        getRelativeChange(mauPerSessionMM._2, prevMauPerSessionMM._2)
      ),
      mauTotalSessionMMChange = (
        getRelativeChange(mauTotalSessionMM._1, prevMauTotalSessionMM._1),
        getRelativeChange(mauTotalSessionMM._2, prevMauTotalSessionMM._2)
      ),
      dauMauRatio = getAbsoluteChange(last24.size.toDouble, lastMonth.size.toDouble),
    )
  }

  private[couchmate] def getAndAddUserAnalytics(implicit ec: ExecutionContext): DBIO[UserActivityAnalytics] = for {
    report <- getUserAnalytics
    _ <- UserActivityAnalyticsTable.table.insertOrUpdate(report)
  } yield report

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
          ).toSeconds,
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

  private[this] def getRelativeChange(curr: Double, prev: Double): Double = {
    if (prev == 0 && curr == 0) { 0d }
    else if (prev == 0) { 1d }
    else {
      (curr - prev) / prev
    }
  }

  private[this] def getAbsoluteChange(curr: Double, prev: Double): Double = {
    if (prev == 0) { 0d }
    else { curr / prev }
  }

  private[this] def getPerSessionMM(users: Seq[UserAnalyticSessions]): (Double, Double) = {
    def getPerSessionForUserMM(sessions: Seq[UserAnalyticSession]): (Double, Double) = {
      val mean = {
        if (sessions.isEmpty) 0d
        else sessions.map(_.duration).sum.toDouble / sessions.size.toDouble
      }
      val sorted = sessions.map(_.duration).sortWith(_ < _)
      val median = getMedian(sorted)
      (mean, median)
    }

    val sessions = users.map(_.sessions).map(getPerSessionForUserMM)
    val mean = {
      if (sessions.isEmpty) 0d
      else sessions.map(_._1).sum / sessions.size
    }

    (
      mean,
      getMedian(sessions.map(_._1).sortWith(_ < _))
    )
  }

  private[this] def getTotalSessionMM(sessions: Seq[UserAnalyticSessions]): (Double, Double) = {
    val totals = sessions.map(_.sessions.map(_.duration).sum)
    val mean = {
      if (totals.isEmpty) 0d
      else totals.sum.toDouble / totals.size.toDouble
    }

    (
      mean,
      getMedian(totals.sortWith(_ < _))
    )
  }

  private[this] def getMedian[T: Numeric](values: Seq[T]): Double = {
    if (values.isEmpty) 0d
    else if (values.size == 1) implicitly[Numeric[T]].toDouble(values.head)
    else if (values.size % 2 == 1) implicitly[Numeric[T]].toDouble(values(values.size / 2))
    else {
      val (up, down) = values.splitAt(values.size / 2)
      (implicitly[Numeric[T]].toDouble(up.last) + implicitly[Numeric[T]].toDouble(down.head)) / 2d
    }
  }
}
