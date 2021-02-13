package com.couchmate.common.dao

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserNotificationTeam
import com.couchmate.common.tables.UserNotificationTeamTable

import scala.concurrent.{ExecutionContext, Future}

trait UserNotificationTeamDAO {
  def getUserTeamNotifications(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationTeam]] =
    db.run(UserNotificationTeamDAO.getUserTeamNotifications(userId))

  def getNotificationsForTeam(teamId: Long)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationTeam]] =
    db.run(UserNotificationTeamDAO.getNotificationsForTeam(teamId))

  def getNotificationsForTeamAndProviderChannel(
    teamId: Long,
    providerChannelId: Long
  )(implicit db: Database): Future[Seq[UserNotificationTeam]] =
    db.run(UserNotificationTeamDAO.getNotificationsForTeamAndProviderChannel(
      teamId, providerChannelId
    ))

  def getUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerChannelId: Long
  )(
    implicit
    db: Database
  ): Future[Option[UserNotificationTeam]] =
    db.run(UserNotificationTeamDAO.getUserTeamNotification(
      userId, teamId, providerChannelId
    ))

  def getNotificationsForSportEvent(sportEventId: Long)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationTeam]] =
    db.run(UserNotificationTeamDAO.getNotificationsForSportEvent(sportEventId))

  def removeUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationTeamDAO.removeUserTeamNotification(
      userId, teamId, providerId
    ))

  def addOrGetUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Option[UserNotificationTeam]] =
    db.run(UserNotificationTeamDAO.addOrGetUserTeamNotification(
      userId, teamId, providerId, hash
    ))

  def toggleUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    enabled: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationTeamDAO.toggleUserTeamNotification(
      userId, teamId, providerId, hash, enabled
    ))

  def toggleOnlyNewUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationTeamDAO.toggleOnlyNewUserTeamNotification(
      userId, teamId, providerId, hash, onlyNew
    ))

  def updateHashUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationTeamDAO.updateHashUserTeamNotification(
      userId, teamId, providerId, hash
    ))
}

object UserNotificationTeamDAO {
  private[this] lazy val getUserTeamNotificationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationTeamTable
        .table
        .filter(_.userId === userId)
        .sortBy(_.name)
  }

  private[common] def getUserTeamNotifications(userId: UUID): DBIO[Seq[UserNotificationTeam]] =
    getUserTeamNotificationsQuery(userId).result

  private[this] lazy val getNotificationsForTeamQuery = Compiled {
    (sportOrganizationTeamId: Rep[Long]) =>
      UserNotificationTeamTable.table.filter(_.teamId === sportOrganizationTeamId)
  }

  private[common] def getNotificationsForTeam(sportOrganizationTeamId: Long): DBIO[Seq[UserNotificationTeam]] =
    getNotificationsForTeamQuery(sportOrganizationTeamId).result

  private[this] lazy val getNotificationsForTeamAndProviderChannelQuery = Compiled {
    (sportOrganizationTeamId: Rep[Long], providerId: Rep[Long]) =>
      UserNotificationTeamTable.table.filter { uNT =>
        uNT.teamId === sportOrganizationTeamId &&
        uNT.providerId === providerId
      }
  }

  private[common] def getNotificationsForTeamAndProviderChannel(
    sportOrganizationTeamId: Long,
    providerId: Long
  ): DBIO[Seq[UserNotificationTeam]] =
    getNotificationsForTeamAndProviderChannelQuery(sportOrganizationTeamId, providerId).result

  private[this] lazy val getUserTeamNotificationQuery = Compiled {
    (userId: Rep[UUID], teamId: Rep[Long], providerId: Rep[Long]) =>
      UserNotificationTeamTable.table.filter { uNT =>
        uNT.userId === userId &&
        uNT.teamId === teamId &&
        uNT.providerId === providerId
      }
  }

  private[common] def getUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long
  ): DBIO[Option[UserNotificationTeam]] =
    getUserTeamNotificationQuery(userId, teamId, providerId).result.headOption

  private[common] def getNotificationsForSportEvent(sportEventId: Long)(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[UserNotificationTeam]] = for {
    sET <- SportEventTeamDAO.getSportEventTeams(sportEventId)
    notifications <- DBIO.fold(
      sET.map(team => getNotificationsForTeam(team.sportOrganizationTeamId)),
      Seq.empty
    )(_ ++ _)
  } yield notifications

  private[common] def addUserTeamNotification(notification: UserNotificationTeam): DBIO[UserNotificationTeam] =
    (UserNotificationTeamTable.table returning UserNotificationTeamTable.table ) += notification

  private[common] def removeUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] =
    UserNotificationTeamTable.table.filter { uNT =>
      uNT.userId === userId &&
      uNT.teamId === teamId &&
      uNT.providerId === providerId
    }.delete.map(_ > 0)

  private[common] def toggleUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    enabled: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[Boolean] = for {
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
    success <- (for {
      uNT <- UserNotificationTeamTable.table if (
        uNT.userId === userId &&
        uNT.teamId === teamId &&
        uNT.providerId === providerId
      )
    } yield uNT.active).update(enabled).map(_ > 0)
  } yield success

  private[common] def toggleOnlyNewUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[Boolean] = for {
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
    success <- (for {
      uNT <- UserNotificationTeamTable.table if (
        uNT.userId === userId &&
        uNT.teamId === teamId &&
        uNT.providerId === providerId
      )
    } yield uNT.onlyNew).update(onlyNew).map(_ > 0)
  } yield success

  private[common] def updateHashUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[Boolean] = for {
    notification <- addOrGetUserTeamNotification(
      userId, teamId, providerId, hash
    )
    success <- (for {
      uNT <- UserNotificationTeamTable.table if (
      uNT.userId === userId &&
      uNT.teamId === teamId &&
      uNT.providerId === providerId
    )
    } yield uNT.hash).update(hash).map(_ > 0)
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

  private[common] def addOrGetUserTeamNotification(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Option[UserNotificationTeam]] = for {
    exists <- getUserTeamNotification(
      userId,
      teamId,
      providerId
    )
    uNT <- exists
      .map(n => DBIO.successful(Some(n)))
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
        case _ => DBIO.successful(Option.empty)
      })
  } yield uNT
}