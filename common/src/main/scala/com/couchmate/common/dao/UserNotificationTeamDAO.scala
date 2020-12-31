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

  def getUserTeamNotification(userId: UUID, teamId: Long)(
    implicit
    db: Database
  ): Future[Option[UserNotificationTeam]] =
    db.run(UserNotificationTeamDAO.getUserTeamNotification(
      userId, teamId
    ))

  def addUserTeamNotification(userId: UUID, teamId: Long)(
    implicit
    db: Database
  ): Future[UserNotificationTeam] =
    db.run(UserNotificationTeamDAO.addUserTeamNotification(
      userId, teamId
    ))

  def removeUserTeamNotification(userId: UUID, teamId: Long)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationTeamDAO.removeUserTeamNotification(
      userId, teamId
    ))

  def addOrGetUserTeamNotification(userId: UUID, teamId: Long)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationTeam] =
    db.run(UserNotificationTeamDAO.addOrGetUserTeamNotification(
      userId, teamId
    ))
}

object UserNotificationTeamDAO {
  private[this] val getUserTeamNotificationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationTeamTable.table.filter(_.userId === userId)
  }

  private[common] def getUserTeamNotifications(userId: UUID): DBIO[Seq[UserNotificationTeam]] =
    getUserTeamNotificationsQuery(userId).result

  private[this] val getNotificationsForTeamQuery = Compiled {
    (teamId: Rep[Long]) =>
      UserNotificationTeamTable.table.filter(_.teamId === teamId)
  }

  private[common] def getNotificationsForTeam(teamId: Long): DBIO[Seq[UserNotificationTeam]] =
    getNotificationsForTeamQuery(teamId).result

  private[this] val getUserTeamNotificationQuery = Compiled {
    (userId: Rep[UUID], teamId: Rep[Long]) =>
      UserNotificationTeamTable.table.filter { uNT =>
        uNT.userId === userId &&
        uNT.teamId === teamId
      }
  }

  private[common] def getUserTeamNotification(userId: UUID, teamId: Long): DBIO[Option[UserNotificationTeam]] =
    getUserTeamNotificationQuery(userId, teamId).result.headOption

  private[common] def addUserTeamNotification(userId: UUID, teamId: Long): DBIO[UserNotificationTeam] =
    (UserNotificationTeamTable.table returning UserNotificationTeamTable.table ) += UserNotificationTeam(
      userId, teamId
    )

  private[common] def removeUserTeamNotification(userId: UUID, teamId: Long)(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] =
    UserNotificationTeamTable.table.filter { uNT =>
      uNT.userId === userId &&
      uNT.teamId === teamId
    }.delete.map(_ > 0)

  private[common] def addOrGetUserTeamNotification(userId: UUID, teamId: Long)(
    implicit
    ec: ExecutionContext
  ): DBIO[UserNotificationTeam] = for {
    exists <- getUserTeamNotification(userId, teamId)
    uNT <- exists.map(DBIO.successful).getOrElse(addUserTeamNotification(
      userId, teamId
    ))
  } yield uNT
}