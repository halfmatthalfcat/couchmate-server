package com.couchmate.common.dao

import java.util.UUID
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserNotificationSeries
import com.couchmate.common.tables.{EpisodeTable, ProviderChannelTable, SeriesTable, UserNotificationQueueTable, UserNotificationSeriesTable}
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object UserNotificationSeriesDAO {
  private[this] lazy val getUserSeriesNotificationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationSeriesTable
        .table
        .filter(_.userId === userId)
        .sortBy(_.name)
  }

  def getUserSeriesNotifications(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationSeries]] =
    db.run(getUserSeriesNotificationsQuery(userId).result)

  private[this] lazy val getNotificationsForSeriesAndProviderChannelQuery = Compiled {
    (seriesId: Rep[Long], providerChannelId: Rep[Long]) =>
      UserNotificationSeriesTable.table.filter { uNS =>
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      }
  }

  def getNotificationsForSeries(
    seriesId: Long,
    channelId: Long
  )(
    implicit
    db: Database
  ): Future[Seq[UserNotificationSeries]] =
    db.run(getNotificationsForSeriesAndProviderChannelQuery(
      seriesId,
      channelId
    ).result)


  private[this] lazy val getUserSeriesNotificationQuery = Compiled {
    (userId: Rep[UUID], seriesId: Rep[Long], providerChannelId: Rep[Long]) =>
      UserNotificationSeriesTable.table.filter { uNS =>
        uNS.userId === userId &&
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      }
  }

  def getUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long
  )(
    implicit
    db: Database
  ): Future[Option[UserNotificationSeries]] =
    db.run(getUserSeriesNotificationQuery(
      userId,
      seriesId,
      providerChannelId
    ).result.headOption)


  def getNotificationsForEpisode(
    episodeId: Long,
    providerChannelId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[UserNotificationSeries]] = for {
    exists <- SeriesDAO.getSeriesByEpisode(episodeId)
    notifications <- exists.fold(Future.successful(Seq.empty[UserNotificationSeries]))(
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

  def getNotificationsForProviderAndEpisode(
    providerId: Long,
    episodeId: Long
  )(implicit db: Database): Future[Seq[UserNotificationSeries]] = db.run(
    getNotificationsForProviderAndEpisodeQuery(providerId, episodeId).result
  )

  def toggleUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    enabled: Boolean
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Boolean] = for {
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
    success <- db.run((for {
      uNS <- UserNotificationSeriesTable.table if (
        uNS.userId === userId &&
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      )
    } yield uNS.active).update(enabled).map(_ > 0))
  } yield success

  def toggleOnlyNewUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Boolean] = for {
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
    success <- db.run((for {
      uNS <- UserNotificationSeriesTable.table if (
        uNS.userId === userId &&
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      )
    } yield uNS.onlyNew).update(onlyNew).map(_ > 0))
  } yield success

  def updateHashUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Boolean] = for {
    notification <- addOrGetUserSeriesNotification(
      userId, seriesId, providerChannelId, hash
    )
    _ <- db.run((for {
      uNS <- UserNotificationSeriesTable.table if (
        uNS.userId === userId &&
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
      )
    } yield uNS.hash).update(hash).map(_ > 0))
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

  def addUserSeriesNotification(
    notification: UserNotificationSeries
  )(implicit db: Database): Future[UserNotificationSeries] = db.run(
    (UserNotificationSeriesTable.table returning UserNotificationSeriesTable.table) += notification
  )

  def removeUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] = db.run(
    UserNotificationSeriesTable.table.filter { uNS =>
      uNS.userId === userId &&
        uNS.seriesId === seriesId &&
        uNS.providerChannelId === providerChannelId
    }.delete.map(_ > 0)
  )

  def addOrGetUserSeriesNotification(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[UserNotificationSeries]] = for {
    exists <- getUserSeriesNotification(
      userId,
      seriesId,
      providerChannelId
    )
    uSN <- exists
      .map(n => Future.successful(Some(n)))
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
        case _ => Future.successful(Option.empty)
      })
  } yield uSN
}