package com.couchmate.common.dao

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserNotificationSeries
import com.couchmate.common.tables.UserNotificationSeriesTable

import scala.concurrent.{ExecutionContext, Future}

trait UserNotificationSeriesDAO {

  def getUserSeriesNotifications(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationSeries]] =
    db.run(UserNotificationSeriesDAO.getUserSeriesNotifications(userId))

  def getNotificationsForSeries(seriesId: Long)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationSeries]] =
    db.run(UserNotificationSeriesDAO.getNotificationsForSeries(seriesId))

  def getUserSeriesNotification(userId: UUID, seriesId: Long)(
    implicit
    db: Database
  ): Future[Option[UserNotificationSeries]] =
    db.run(UserNotificationSeriesDAO.getUserSeriesNotification(
      userId, seriesId
    ))

  def getNotificationsForEpisode(episodeId: Long)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationSeries]] =
    db.run(UserNotificationSeriesDAO.getNotificationsForEpisode(episodeId))

  def addUserSeriesNotification(
    userId: UUID,
    seriesId: Long
  )(
    implicit
    db: Database
  ): Future[UserNotificationSeries] =
    db.run(UserNotificationSeriesDAO.addUserSeriesNotification(
      userId, seriesId
    ))

  def removeUserSeriesNotification(
    userId: UUID,
    seriesId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Boolean] =
    db.run(UserNotificationSeriesDAO.removeUserSeriesNotification(
      userId, seriesId
    ))

  def addOrGetUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    hash: Option[String] = None
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationSeries] =
    db.run(UserNotificationSeriesDAO.addOrGetUserSeriesNotification(
      userId, seriesId, hash
    ))
}

object UserNotificationSeriesDAO {
  private[this] lazy val getUserSeriesNotificationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationSeriesTable.table.filter(_.userId === userId)
  }

  private[common] def getUserSeriesNotifications(userId: UUID): DBIO[Seq[UserNotificationSeries]] =
    getUserSeriesNotificationsQuery(userId).result

  private[this] lazy val getNotificationsForSeriesQuery = Compiled {
    (seriesId: Rep[Long]) =>
      UserNotificationSeriesTable.table.filter(_.seriesId === seriesId)
  }

  private[common] def getNotificationsForSeries(seriesId: Long): DBIO[Seq[UserNotificationSeries]] =
    getNotificationsForSeriesQuery(seriesId).result

  private[this] lazy val getUserSeriesNotificationQuery = Compiled {
    (userId: Rep[UUID], seriesId: Rep[Long]) =>
      UserNotificationSeriesTable.table.filter { uNS =>
        uNS.userId === userId &&
        uNS.seriesId === seriesId
      }
  }

  private[common] def getNotificationsForEpisode(episodeId: Long)(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[UserNotificationSeries]] = for {
    exists <- SeriesDAO.getSeriesByEpisode(episodeId)
    notifications <- exists.fold[DBIO[Seq[UserNotificationSeries]]](DBIO.successful(Seq.empty))(
      series => getNotificationsForSeries(series.seriesId.get))
  } yield notifications

  private[common] def getUserSeriesNotification(userId: UUID, seriesId: Long): DBIO[Option[UserNotificationSeries]] =
    getUserSeriesNotificationQuery(userId, seriesId).result.headOption

  private[common] def addUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    hash: Option[String] = None
  ): DBIO[UserNotificationSeries] =
    (UserNotificationSeriesTable.table returning UserNotificationSeriesTable.table) += UserNotificationSeries(
      userId, seriesId, hash, LocalDateTime.now()
    )

  private[common] def removeUserSeriesNotification(
    userId: UUID,
    seriesId: Long
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] =
    UserNotificationSeriesTable.table.filter { uNS =>
      uNS.userId === userId &&
      uNS.seriesId === seriesId
    }.delete.map(_ > 0)

  private[common] def addOrGetUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    hash: Option[String] = None
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[UserNotificationSeries] = for {
    exists <- getUserSeriesNotification(userId, seriesId)
    uSN <- exists
      .map(DBIO.successful)
      .getOrElse(addUserSeriesNotification(userId, seriesId, hash))
  } yield uSN
}