package com.couchmate.common.dao

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Airing, ApplicationPlatform, Show, UserNotificationConfiguration, UserNotificationQueueItem, UserNotificationSeries, UserNotificationShow, UserNotificationTeam}
import com.couchmate.common.tables.UserNotificationQueueTable

import scala.concurrent.{ExecutionContext, Future}

trait UserNotificationQueueDAO {

  def getUserNotificationQueueItemsForUser(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.getUserNotificationQueueItemsForUser(userId))

  def getUserNotificationQueueItemsForUserAndPlatform(
    userId: UUID,
    platform: ApplicationPlatform
  )(implicit db: Database): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.getUserNotificationQueueItemsForUserAndPlatform(
      userId, platform
    ))

  def getUserNotificationItemsForDeliveryAt(deliveryAt: LocalDateTime)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.getUserNotificationItemsForDeliveryAt(deliveryAt))

  def getUserNotificationItemForUserAiringAndPlatform(
    userId: UUID,
    airingId: String,
    platform: ApplicationPlatform,
    deliverAt: LocalDateTime
  )(implicit db: Database): Future[Option[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.getUserNotificationItemForUserAiringAndPlatform(
      userId, airingId, platform, deliverAt
    ))

  def addOrGetUserNotificationQueueItem(item: UserNotificationQueueItem)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationQueueItem] =
    db.run(UserNotificationQueueDAO.addOrGetUserNotificationQueueItem(item))

  def deliverUserNotificationItem(
    item: UserNotificationQueueItem,
    success: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationQueueItem] =
    db.run(UserNotificationQueueDAO.deliverUserNotificationItem(
      item, success
    ))

  def addUserNotificationsForShow(airingId: String)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationsForShow(
      airingId
    ))

  def addUserNotificationForShow(
    airingId: String,
    userId: UUID,
    hash: Option[String]
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationForShow(
      airingId, userId, hash
    ))

  def addUserNotificationsForEpisode(
    airingId: String,
    episodeId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationsForEpisode(
      airingId, episodeId
    ))

  def addUserNotificationForSeries(
    seriesId: Long,
    userId: UUID,
    hash: Option[String],
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationForSeries(
      seriesId, userId, hash, onlyNew
    ))

  def addUserNotificationsForSport(
    airingId: String,
    sportEventId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationsForSport(
      airingId, sportEventId
    ))

  def addUserNotificationForSportTeam(
    sportTeamId: Long,
    userId: UUID,
    hash: Option[String],
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationForSportTeam(
      sportTeamId, userId, hash, onlyNew
    ))

}

object UserNotificationQueueDAO {
  private[this] lazy val getUserNotificationQueueItemsForUserQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationQueueTable.table.filter(_.userId === userId)
  }

  private[common] def getUserNotificationQueueItemsForUser(userId: UUID): DBIO[Seq[UserNotificationQueueItem]] =
    getUserNotificationQueueItemsForUserQuery(userId).result

  private[this] lazy val getUserNotificationQueueItemsForUserAndPlatformQuery = Compiled {
    (userId: Rep[UUID], platform: Rep[ApplicationPlatform]) =>
      UserNotificationQueueTable.table.filter { uNQ =>
        uNQ.userId === userId &&
        uNQ.platform === platform
      }
  }

  private[common] def getUserNotificationQueueItemsForUserAndPlatform(
    userId: UUID,
    platform: ApplicationPlatform
  ): DBIO[Seq[UserNotificationQueueItem]] =
    getUserNotificationQueueItemsForUserAndPlatformQuery(userId, platform).result

  private[this] lazy val getUserNotificationItemsForDeliveryAtQuery = Compiled {
    (deliverAt: Rep[LocalDateTime]) =>
      UserNotificationQueueTable.table.filter(_.deliverAt === deliverAt)
  }

  private[common] def getUserNotificationItemsForDeliveryAt(deliverAt: LocalDateTime): DBIO[Seq[UserNotificationQueueItem]] =
    getUserNotificationItemsForDeliveryAtQuery(deliverAt).result

  private[this] lazy val getUserNotificationItemForUserAiringAndPlatformQuery = Compiled {
    (userId: Rep[UUID], airingId: Rep[String], platform: Rep[ApplicationPlatform], deliverAt: Rep[LocalDateTime]) =>
      UserNotificationQueueTable.table.filter { uNQ =>
        uNQ.userId === userId &&
        uNQ.airingId === airingId &&
        uNQ.platform === platform &&
        uNQ.deliverAt === deliverAt
      }
  }

  private[common] def getUserNotificationItemForUserAiringAndPlatform(
    userId: UUID,
    airingId: String,
    platform: ApplicationPlatform,
    deliverAt: LocalDateTime
  ): DBIO[Option[UserNotificationQueueItem]] =
    getUserNotificationItemForUserAiringAndPlatformQuery(
      userId, airingId, platform, deliverAt
    ).result.headOption

  private[common] def addUserNotificationsForShow(airingId: String)(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    airing <- AiringDAO.getAiring(airingId)
    users <- airing.fold[DBIO[Seq[UserNotificationShow]]](DBIO.successful(Seq.empty[UserNotificationShow]))(
      a => UserNotificationShowDAO.getNotificationsForShow(airingId).map(_.filter(_.onlyNew == a.isNew))
    )
    configurations <- DBIO.fold(
      users.map(user => UserNotificationConfigurationDAO.getUserNotificationConfigurations(
        user.userId
      )),
      Seq.empty
    )(_ ++ _)
    notifications <- DBIO.sequence(
      configurations.map(configuration => addOrGetUserNotificationQueueItem(UserNotificationQueueItem(
        notificationId = UUID.randomUUID(),
        userId = configuration.userId,
        airingId = airingId,
        hash = users.find(_.userId == configuration.userId).flatMap(_.hash),
        applicationPlatform = configuration.platform,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.get.startTime.minusMinutes(15)
      )))
    )
  } yield notifications

  private[common] def addUserNotificationForShow(
    airingId: String,
    userId: UUID,
    hash: Option[String]
  )(implicit ec: ExecutionContext): DBIO[Seq[UserNotificationQueueItem]] = for {
    airing <- AiringDAO.getAiring(airingId)
    _ <- UserNotificationShowDAO.addOrGetUserShowNotification(
      userId, airingId, hash, true
    )
    configurations <- airing.fold[DBIO[Seq[UserNotificationConfiguration]]](DBIO.successful(Seq.empty))(
      _ => UserNotificationConfigurationDAO.getUserNotificationConfigurations(userId)
    )
    notifications <- DBIO.sequence(
      configurations.map(configuration => addOrGetUserNotificationQueueItem(UserNotificationQueueItem(
        notificationId = UUID.randomUUID(),
        userId = configuration.userId,
        airingId = airingId,
        hash = hash,
        applicationPlatform = configuration.platform,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.get.startTime.minusMinutes(15)
      ))))
  } yield notifications

  private[common] def addUserNotificationsForEpisode(
    airingId: String,
    episodeId: Long
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    airing <- AiringDAO.getAiring(airingId)
    users <- airing.fold[DBIO[Seq[UserNotificationSeries]]](DBIO.successful(Seq.empty))(
      _ => UserNotificationSeriesDAO.getNotificationsForEpisode(episodeId)
    )
    configurations <- DBIO.fold(
      users.map(user => UserNotificationConfigurationDAO.getUserNotificationConfigurations(
        user.userId
      )),
      Seq.empty
    )(_ ++ _)
    notifications <- DBIO.sequence(
      configurations.map(configuration => addOrGetUserNotificationQueueItem(UserNotificationQueueItem(
        notificationId = UUID.randomUUID(),
        userId = configuration.userId,
        airingId = airingId,
        hash = users.find(_.userId == configuration.userId).flatMap(_.hash),
        applicationPlatform = configuration.platform,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.get.startTime.minusMinutes(15)
      )))
    )
  } yield notifications

  private[common] def addUserNotificationForSeries(
    seriesId: Long,
    userId: UUID,
    hash: Option[String],
    onlyNew: Boolean
  )(implicit ec: ExecutionContext): DBIO[Seq[UserNotificationQueueItem]] = for {
    series <- SeriesDAO.getSeries(seriesId)
    airings <- series.fold[DBIO[Seq[Airing]]](DBIO.successful(Seq.empty))(
      _ => SeriesDAO.getUpcomingSeriesAirings(seriesId)
    )
    _ <- UserNotificationSeriesDAO.addOrGetUserSeriesNotification(
      userId, seriesId, hash, onlyNew
    )
    configurations <- UserNotificationConfigurationDAO.getUserNotificationConfigurations(userId)
    n = airings.foldLeft[Seq[UserNotificationQueueItem]](Seq.empty)((acc, airing) => acc ++ configurations.map(
      configuration => UserNotificationQueueItem(
        notificationId = UUID.randomUUID(),
        userId = configuration.userId,
        airingId = airing.airingId.get,
        hash = hash,
        applicationPlatform = configuration.platform,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.startTime.minusMinutes(15)
      )
    ))
    notifications <- DBIO.sequence(n.map(addOrGetUserNotificationQueueItem))
  } yield notifications

  private[common] def addUserNotificationsForSport(
    airingId: String,
    sportEventId: Long
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    teams <- SportEventTeamDAO.getSportEventTeams(sportEventId)
    airing <- AiringDAO.getAiring(airingId)
    users <- airing.fold[DBIO[Seq[UserNotificationTeam]]](DBIO.successful(Seq.empty))(
      a => DBIO.fold(
        teams.map(team => UserNotificationTeamDAO.getNotificationsForTeam(team.sportTeamId)),
        Seq.empty
      )(_ ++ _.filter(_.onlyNew == a.isNew))
    )
    configurations <- DBIO.fold(
      users.map(user => UserNotificationConfigurationDAO.getUserNotificationConfigurations(
        user.userId
      )),
      Seq.empty
    )(_ ++ _)
    notifications <- DBIO.sequence(
      configurations.map(configuration => addOrGetUserNotificationQueueItem(UserNotificationQueueItem(
        notificationId = UUID.randomUUID(),
        userId = configuration.userId,
        airingId = airingId,
        hash = users.find(_.userId == configuration.userId).flatMap(_.hash),
        applicationPlatform = configuration.platform,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.get.startTime.minusMinutes(15)
      )))
    )
  } yield notifications

  private[common] def addUserNotificationForSportTeam(
    sportTeamId: Long,
    userId: UUID,
    hash: Option[String],
    onlyNew: Boolean
  )(implicit ec: ExecutionContext): DBIO[Seq[UserNotificationQueueItem]] = for {
    team <- SportTeamDAO.getSportTeam(sportTeamId)
    airings <- team.fold[DBIO[Seq[Airing]]](DBIO.successful(Seq.empty))(
      _ => SportTeamDAO.getUpcomingSportTeamAirings(sportTeamId)
    )
    _ <- UserNotificationTeamDAO.addOrGetUserTeamNotification(
      userId, sportTeamId, hash, onlyNew
    )
    configurations <- UserNotificationConfigurationDAO.getUserNotificationConfigurations(userId)
    n = airings.foldLeft[Seq[UserNotificationQueueItem]](Seq.empty)((acc, airing) => acc ++ configurations.map(
      configuration => UserNotificationQueueItem(
        notificationId = UUID.randomUUID(),
        userId = configuration.userId,
        airingId = airing.airingId.get,
        hash = hash,
        applicationPlatform = configuration.platform,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.startTime.minusMinutes(15)
      )
    ))
    notifications <- DBIO.sequence(n.map(addOrGetUserNotificationQueueItem))
  } yield notifications

  private[common] def addOrGetUserNotificationQueueItem(item: UserNotificationQueueItem)(
    implicit
    ec: ExecutionContext
  ): DBIO[UserNotificationQueueItem] = for {
    exists <- getUserNotificationItemForUserAiringAndPlatform(
      item.userId, item.airingId, item.applicationPlatform, item.deliverAt
    )
    uNQ <- exists.map(DBIO.successful).getOrElse(
      (UserNotificationQueueTable.table returning UserNotificationQueueTable.table) += item
    )
  } yield uNQ

  private[common] def deliverUserNotificationItem(
    item: UserNotificationQueueItem,
    success: Boolean
  )(implicit ec: ExecutionContext): DBIO[UserNotificationQueueItem] = for {
    exists <- getUserNotificationItemForUserAiringAndPlatform(
      item.userId, item.airingId, item.applicationPlatform, item.deliverAt
    )
    toInsert = exists.map(_.copy(
      deliveredAt = Some(LocalDateTime.now()),
      success = success
    )).getOrElse(UserNotificationQueueItem(
      notificationId = UUID.randomUUID(),
      userId = item.userId,
      airingId = item.airingId,
      applicationPlatform = item.applicationPlatform,
      hash = None,
      deliverAt = LocalDateTime.now(),
      deliveredAt = Some(LocalDateTime.now()),
      success = success
    ))
    uNQ <- exists.map(_ => (for {
      cUNQ <- UserNotificationQueueTable.table if (
        cUNQ.userId === item.userId &&
        cUNQ.airingId === item.airingId &&
        cUNQ.platform === item.applicationPlatform &&
        cUNQ.deliverAt === item.deliverAt
      )
    } yield (cUNQ.deliveredAt, cUNQ.success)).update((
      Some(LocalDateTime.now()), success
    )).map(_ => toInsert)).getOrElse(
      (UserNotificationQueueTable.table returning UserNotificationQueueTable.table) += toInsert
    )
  } yield uNQ
}
