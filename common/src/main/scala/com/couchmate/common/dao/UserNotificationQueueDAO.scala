package com.couchmate.common.dao

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{Airing, ApplicationPlatform, UserNotificationQueueItem}
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

  def addUserNotificationsForShow(
    airingId: String,
    deliverAt: LocalDateTime
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationsForShow(
      airingId, deliverAt
    ))

  def addUserNotificationsForEpisode(
    airingId: String,
    episodeId: Long,
    deliverAt: LocalDateTime
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationsForEpisode(
      airingId, episodeId, deliverAt
    ))

  def addUserNotificationsForSport(
    airingId: String,
    sportEventId: Long,
    deliverAt: LocalDateTime
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationsForSport(
      airingId, sportEventId, deliverAt
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

  private[common] def addUserNotificationsForShow(
    airingId: String,
    deliverAt: LocalDateTime
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    users <- UserNotificationShowDAO.getNotificationsForShow(airingId)
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
        deliverAt = deliverAt
      )))
    )
  } yield notifications

  private[common] def addUserNotificationsForEpisode(
    airingId: String,
    episodeId: Long,
    deliverAt: LocalDateTime
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    users <- UserNotificationSeriesDAO.getNotificationsForEpisode(episodeId)
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
        deliverAt = deliverAt
      )))
    )
  } yield notifications

  private[common] def addUserNotificationsForSport(
    airingId: String,
    sportEventId: Long,
    deliverAt: LocalDateTime
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    teams <- SportEventTeamDAO.getSportEventTeams(sportEventId)
    users <- DBIO.fold(
      teams.map(team => UserNotificationTeamDAO.getNotificationsForTeam(team.sportTeamId)),
      Seq.empty
    )(_ ++ _)
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
        deliverAt = deliverAt
      )))
    )
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
