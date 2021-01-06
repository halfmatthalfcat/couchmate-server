package com.couchmate.common.dao

import java.time.LocalDateTime
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{UserNotificationQueueItem, UserNotificationShow}
import com.couchmate.common.tables.UserNotificationShowTable

import scala.concurrent.{ExecutionContext, Future}

trait UserNotificationShowDAO {

  def getUserShowNotifications(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationShow]] =
    db.run(UserNotificationShowDAO.getUserShowNotifications(userId))

  def getNotificationsForShow(airingId: String)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationShow]] =
    db.run(UserNotificationShowDAO.getNotificationsForShow(airingId))

  def getUserShowNotification(userId: UUID, airingId: String)(
    implicit
    db: Database
  ): Future[Option[UserNotificationShow]] =
    db.run(UserNotificationShowDAO.getUserShowNotification(userId, airingId))

  def addUserShowNotification(
    userId: UUID,
    airingId: String
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationForShow(
      airingId, userId, None
    ))

  def removeUserShowNotification(
    userId: UUID,
    airingId: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationShowDAO.removeUserShowNotification(
      userId, airingId
    ))

  def addOrGetUserShowNotification(
    userId: UUID,
    airingId: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationShow] =
    db.run(UserNotificationShowDAO.addOrGetUserShowNotification(
      userId, airingId, None, onlyNew
    ))
}

object UserNotificationShowDAO {
  private[this] lazy val getUserShowNotificationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationShowTable.table.filter(_.userId === userId)
  }

  private[common] def getUserShowNotifications(userId: UUID): DBIO[Seq[UserNotificationShow]] =
    getUserShowNotificationsQuery(userId).result

  private[this] lazy val getNotificationsForShowQuery = Compiled {
    (airingId: Rep[String]) =>
      UserNotificationShowTable.table.filter(_.airingId === airingId)
  }

  private[common] def getNotificationsForShow(airingId: String): DBIO[Seq[UserNotificationShow]] =
    getNotificationsForShowQuery(airingId).result

  private[this] lazy val getUserShowNotificationQuery = Compiled {
    (userId: Rep[UUID], airingId: Rep[String]) =>
      UserNotificationShowTable.table.filter { uNS =>
        uNS.userId === userId &&
          uNS.airingId === airingId
      }
  }

  private[common] def getUserShowNotification(userId: UUID, airingId: String): DBIO[Option[UserNotificationShow]] =
    getUserShowNotificationQuery(userId, airingId).result.headOption

  private[common] def addUserShowNotification(
    userId: UUID,
    airingId: String,
    hash: Option[String] = None,
    onlyNew: Boolean = false
  ): DBIO[UserNotificationShow] =
    (UserNotificationShowTable.table returning UserNotificationShowTable.table) += UserNotificationShow(
      userId, airingId, hash, onlyNew, LocalDateTime.now()
    )

  private[common] def removeUserShowNotification(userId: UUID, airingId: String)(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] =
    UserNotificationShowTable.table.filter { uSN =>
      uSN.userId === userId &&
      uSN.airingId === airingId
    }.delete.map(_ > 0)

  private[common] def addOrGetUserShowNotification(
    userId: UUID,
    airingId: String,
    hash: Option[String] = None,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[UserNotificationShow] = for {
    exists <- getUserShowNotification(userId, airingId)
    uSN <- exists
      .map(DBIO.successful)
      .getOrElse(addUserShowNotification(userId, airingId, hash, onlyNew))
  } yield uSN
}
