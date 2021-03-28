package com.couchmate.common.dao

import java.util.UUID
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserNotificationTeam
import com.couchmate.common.tables.UserNotificationTeamTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object UserNotificationTeamDAO {
  private[this] lazy val getUserTeamNotificationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationTeamTable
        .table
        .filter(_.userId === userId)
        .sortBy(_.name)
  }

  def getUserTeamNotifications(userId: UUID)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[UserNotificationTeam]] = cache(
    "getUserTeamNotifications",
    userId.toString
  )(db.run(getUserTeamNotificationsQuery(userId).result))()

  private[this] lazy val getNotificationsForTeamQuery = Compiled {
    (sportOrganizationTeamId: Rep[Long]) =>
      UserNotificationTeamTable.table.filter(_.teamId === sportOrganizationTeamId)
  }

  def getNotificationsForTeam(teamId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[UserNotificationTeam]] = cache(
    "getNotificationsForTeam",
    teamId
  )(db.run(getNotificationsForTeamQuery(teamId).result))()

  private[this] lazy val getNotificationsForTeamAndProviderChannelQuery = Compiled {
    (sportOrganizationTeamId: Rep[Long], providerId: Rep[Long]) =>
      UserNotificationTeamTable.table.filter { uNT =>
        uNT.teamId === sportOrganizationTeamId &&
        uNT.providerId === providerId
      }
  }

  def getNotificationsForTeamAndProviderChannel(
    teamId: Long,
    providerChannelId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[UserNotificationTeam]] = cache(
    "getNotificationsForTeamAndProviderChannel",
    teamId,
    providerChannelId
  )(db.run(getNotificationsForTeamAndProviderChannelQuery(
    teamId,
    providerChannelId
  ).result))()

  private[this] lazy val getUserTeamNotificationQuery = Compiled {
    (userId: Rep[UUID], teamId: Rep[Long], providerId: Rep[Long]) =>
      UserNotificationTeamTable.table.filter { uNT =>
        uNT.userId === userId &&
        uNT.teamId === teamId &&
        uNT.providerId === providerId
      }
  }

  def getUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
  ): Future[Option[UserNotificationTeam]] = db.run(
    getUserTeamNotificationQuery(userId, teamId, providerId).result.headOption
  )


  def getNotificationsForSportEvent(sportEventId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Seq[UserNotificationTeam]] = for {
    sET <- SportEventTeamDAO.getSportEventTeams(sportEventId)
    notifications <- Future.sequence(
      sET.map(team => getNotificationsForTeam(team.sportOrganizationTeamId)),
    ).map(_.fold(Seq.empty)(_ ++ _))
  } yield notifications

  def addUserTeamNotification(notification: UserNotificationTeam)(
    implicit
    db: Database
  ): Future[UserNotificationTeam] = db.run(
    (UserNotificationTeamTable.table returning UserNotificationTeamTable.table ) += notification
  )

  def removeUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] = db.run(
    UserNotificationTeamTable.table.filter { uNT =>
      uNT.userId === userId &&
        uNT.teamId === teamId &&
        uNT.providerId === providerId
    }.delete.map(_ > 0)
  )

  def toggleUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    enabled: Boolean
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Boolean] = for {
    notification <- addOrGetUserTeamNotification(
      userId, teamId, providerId, hash
    )
    _ <- if (enabled) {
      UserNotificationQueueDAO.addUserNotificationForSportTeam(
        userId,
        teamId,
        providerId,
        // If we get, we want to use the already existing hash
        notification.map(_.hash).getOrElse(hash),
        notification.forall(_.onlyNew)
      )
    } else {
      UserNotificationQueueDAO.removeUserNotificationForTeam(
        userId, teamId, providerId
      )
    }
    success <- db.run((for {
      uNT <- UserNotificationTeamTable.table if (
        uNT.userId === userId &&
        uNT.teamId === teamId &&
        uNT.providerId === providerId
      )
    } yield uNT.active).update(enabled).map(_ > 0))
  } yield success

  def toggleOnlyNewUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Boolean] = for {
    notification <- addOrGetUserTeamNotification(
      userId, teamId, providerId, hash
    )
    _ <- UserNotificationQueueDAO.removeUserNotificationForTeam(
      userId, teamId, providerId
    )
    _ <- UserNotificationQueueDAO.addUserNotificationForSportTeam(
      userId,
      teamId,
      providerId,
      // If we get, we want to use the already existing hash
      notification.map(_.hash).getOrElse(hash),
      onlyNew
    ).map(_ => true)
    success <- db.run((for {
      uNT <- UserNotificationTeamTable.table if (
        uNT.userId === userId &&
        uNT.teamId === teamId &&
        uNT.providerId === providerId
      )
    } yield uNT.onlyNew).update(onlyNew).map(_ > 0))
  } yield success

  def updateHashUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Boolean] = for {
    notification <- addOrGetUserTeamNotification(
      userId, teamId, providerId, hash
    )
    success <- db.run((for {
      uNT <- UserNotificationTeamTable.table if (
      uNT.userId === userId &&
      uNT.teamId === teamId &&
      uNT.providerId === providerId
    )
    } yield uNT.hash).update(hash).map(_ > 0))
    _ <- UserNotificationQueueDAO.removeUserNotificationForTeam(
      userId, teamId, providerId
    )
    _ <- UserNotificationQueueDAO.addUserNotificationForSportTeam(
      userId,
      teamId,
      providerId,
      // If we get, we want to use the already existing hash
      hash,
      notification.forall(_.onlyNew)
    ).map(_ => true)
  } yield success

  def addOrGetUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[UserNotificationTeam]] = for {
    exists <- getUserTeamNotification(
      userId,
      teamId,
      providerId
    )
    uNT <- exists
      .map(n => Future.successful(Some(n)))
      .getOrElse(SportOrganizationTeamDAO.getTeamAndOrgForOrgTeam(teamId).flatMap {
        case (Some(team), Some(org)) => addUserTeamNotification(UserNotificationTeam(
          userId,
          teamId,
          providerId,
          s"${team.name} (${org.orgName.getOrElse(org.sportName)})",
          hash
        )).map(Some(_))
        case (Some(team), _) => addUserTeamNotification(UserNotificationTeam(
          userId,
          teamId,
          providerId,
          team.name,
          hash
        )).map(Some(_))
        case _ => Future.successful(Option.empty)
      })
  } yield uNT
}