package com.couchmate.common.dao

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserNotificationSeries
import com.couchmate.common.tables.{EpisodeTable, ProviderChannelTable, SeriesTable, UserNotificationQueueTable, UserNotificationSeriesTable}

import scala.concurrent.{ExecutionContext, Future}

trait UserNotificationSeriesDAO {

  def getUserSeriesNotifications(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationSeries]] =
    db.run(UserNotificationSeriesDAO.getUserSeriesNotifications(userId))

  def getNotificationsForSeries(
    seriesId: Long,
    channelId: Long
  )(
    implicit
    db: Database
  ): Future[Seq[UserNotificationSeries]] =
    db.run(UserNotificationSeriesDAO.getNotificationsForSeries(seriesId, channelId))

  def getUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long
  )(
    implicit
    db: Database
  ): Future[Option[UserNotificationSeries]] =
    db.run(UserNotificationSeriesDAO.getUserSeriesNotification(
      userId, seriesId, providerChannelId
    ))

  def getNotificationsForEpisode(
    episodeId: Long,
    channelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationSeries]] =
    db.run(UserNotificationSeriesDAO.getNotificationsForEpisode(episodeId, channelId))

  def addUserSeriesNotification(notification: UserNotificationSeries)(
    implicit
    db: Database
  ): Future[UserNotificationSeries] =
    db.run(UserNotificationSeriesDAO.addUserSeriesNotification(notification))

  def removeUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    channelId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Boolean] =
    db.run(UserNotificationSeriesDAO.removeUserSeriesNotification(
      userId, seriesId, channelId
    ))

  def addOrGetUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Option[UserNotificationSeries]] =
    db.run(UserNotificationSeriesDAO.addOrGetUserSeriesNotification(
      userId, seriesId, providerChannelId, hash
    ))

  def toggleUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    enabled: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationSeriesDAO.toggleUserSeriesNotification(
      userId, seriesId, providerChannelId, hash, enabled
    ))

  def toggleOnlyNewUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationSeriesDAO.toggleOnlyNewUserSeriesNotification(
      userId, seriesId, providerChannelId, hash, onlyNew
    ))

  def updateHashUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationSeriesDAO.updateHashUserSeriesNotification(
      userId, seriesId, providerChannelId, hash
    ))
}

object UserNotificationSeriesDAO {
  private[this] lazy val getUserSeriesNotificationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationSeriesTable
        .table
        .filter(_.userId === userId)
        .sortBy(_.name)
  }

  private[common] def getUserSeriesNotifications(userId: UUID): DBIO[Seq[UserNotificationSeries]] =
    getUserSeriesNotificationsQuery(userId).result

  private[this] lazy val getNotificationsForSeriesAndProviderChannelQuery = Compiled {
    (seriesId: Rep[Long], providerChannelId: Rep[Long]) =>
      UserNotificationSeriesTable.table.filter { uNS =>
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      }
  }

  private[common] def getNotificationsForSeries(
    seriesId: Long,
    providerChannelId: Long
  ): DBIO[Seq[UserNotificationSeries]] =
    getNotificationsForSeriesAndProviderChannelQuery(seriesId, providerChannelId).result

  private[this] lazy val getUserSeriesNotificationQuery = Compiled {
    (userId: Rep[UUID], seriesId: Rep[Long], providerChannelId: Rep[Long]) =>
      UserNotificationSeriesTable.table.filter { uNS =>
        uNS.userId === userId &&
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      }
  }

  private[common] def getNotificationsForEpisode(
    episodeId: Long,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[UserNotificationSeries]] = for {
    exists <- SeriesDAO.getSeriesByEpisode(episodeId)
    notifications <- exists.fold[DBIO[Seq[UserNotificationSeries]]](DBIO.successful(Seq.empty))(
      series => getNotificationsForSeries(
        series.seriesId.get,
        providerChannelId
      ))
  } yield notifications

  private[this] lazy val getNotificationsForProviderAndEpisodeQuery = Compiled {
    (providerId: Rep[Long], episodeId: Rep[Long]) => for {
      e <- EpisodeTable.table if e.episodeId === episodeId
      s <- SeriesTable.table if s.seriesId === e.seriesId
      pc <- ProviderChannelTable.table if pc.providerId === providerId
      uNS <- UserNotificationSeriesTable.table if (
        uNS.providerChannelId === pc.providerChannelId &&
        uNS.seriesId === s.seriesId
      )
    } yield uNS
  }

  private[common] def getNotificationsForProviderAndEpisode(
    providerId: Long,
    episodeId: Long
  ): DBIO[Seq[UserNotificationSeries]] =
    getNotificationsForProviderAndEpisodeQuery(providerId, episodeId).result

  private[common] def getUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long
  ): DBIO[Option[UserNotificationSeries]] =
    getUserSeriesNotificationQuery(userId, seriesId, providerChannelId).result.headOption

  private[common] def toggleUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    enabled: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[Boolean] = for {
    notification <- addOrGetUserSeriesNotification(
      userId, seriesId, providerChannelId, hash
    )
    _ <- if (enabled) {
      UserNotificationQueueDAO.addUserNotificationForSeries(
        userId,
        seriesId,
        providerChannelId,
        // If we get, we want to use the already existing hash
        notification.map(_.hash).getOrElse(hash),
        notification.forall(_.onlyNew)
      )
    } else {
      UserNotificationQueueDAO.removeUserNotificationForSeries(
        userId, seriesId, providerChannelId
      )
    }
    success <- (for {
      uNS <- UserNotificationSeriesTable.table if (
        uNS.userId === userId &&
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      )
    } yield uNS.active).update(enabled).map(_ > 0)
  } yield success

  private[common] def toggleOnlyNewUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[Boolean] = for {
    notification <- addOrGetUserSeriesNotification(
      userId, seriesId, providerChannelId, hash
    )
    _ <- UserNotificationQueueDAO.removeUserNotificationForSeries(
      userId, seriesId, providerChannelId
    )
    _ <- UserNotificationQueueDAO.addUserNotificationForSeries(
      userId,
      seriesId,
      providerChannelId,
      // If we get, we want to use the already existing hash
      notification.map(_.hash).getOrElse(hash),
      onlyNew
    ).map(_ => true)
    success <- (for {
      uNS <- UserNotificationSeriesTable.table if (
        uNS.userId === userId &&
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      )
    } yield uNS.onlyNew).update(onlyNew).map(_ > 0)
  } yield success

  private[common] def updateHashUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[Boolean] = for {
    notification <- addOrGetUserSeriesNotification(
      userId, seriesId, providerChannelId, hash
    )
    _ <- (for {
      uNS <- UserNotificationSeriesTable.table if (
        uNS.userId === userId &&
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      )
    } yield uNS.hash).update(hash).map(_ > 0)
    _ <- UserNotificationQueueDAO.removeUserNotificationForSeries(
      userId, seriesId, providerChannelId
    )
    _ <- UserNotificationQueueDAO.addUserNotificationForSeries(
      userId,
      seriesId,
      providerChannelId,
      // If we get, we want to use the already existing hash
      hash,
      notification.forall(_.onlyNew)
    ).map(_ => true)
  } yield true

  private[common] def addUserSeriesNotification(
    notification: UserNotificationSeries
  ): DBIO[UserNotificationSeries] =
    (UserNotificationSeriesTable.table returning UserNotificationSeriesTable.table) += notification

  private[common] def removeUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] =
    UserNotificationSeriesTable.table.filter { uNS =>
      uNS.userId === userId &&
      uNS.seriesId === seriesId &&
      uNS.providerChannelId === providerChannelId
    }.delete.map(_ > 0)

  private[common] def addOrGetUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Option[UserNotificationSeries]] = for {
    exists <- getUserSeriesNotification(
      userId,
      seriesId,
      providerChannelId
    )
    uSN <- exists
      .map(n => DBIO.successful(Some(n)))
      .getOrElse((for {
        channel <- ChannelDAO
          .getChannelForProviderChannel(providerChannelId)
        series <- SeriesDAO
          .getSeries(seriesId)
      } yield (channel, series)).flatMap {
        case (Some(channel), Some(series)) => addUserSeriesNotification(UserNotificationSeries(
          userId,
          seriesId,
          providerChannelId,
          series.seriesName,
          channel.callsign,
          hash
        )).map(Some(_))
        case _ => DBIO.successful(Option.empty)
      })
  } yield uSN
}