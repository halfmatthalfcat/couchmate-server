package com.couchmate.common.dao

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data._
import com.couchmate.common.tables.UserNotificationShowTable

import scala.concurrent.{ExecutionContext, Future}

trait UserNotificationShowDAO {

  def getUserShowNotifications(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationShow]] =
    db.run(UserNotificationShowDAO.getUserShowNotifications(userId))

  def getNotificationsForShow(
    airingId: String,
    providerChannelId: Long
  )(
    implicit
    db: Database
  ): Future[Seq[UserNotificationShow]] =
    db.run(UserNotificationShowDAO.getNotificationsForShow(
      airingId, providerChannelId
    ))

  def getUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long,
  )(
    implicit
    db: Database
  ): Future[Option[UserNotificationShow]] =
    db.run(UserNotificationShowDAO.getUserShowNotification(
      userId, airingId, providerChannelId
    ))

  def addUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationForShow(
      userId, airingId, providerChannelId, hash
    ))

  def removeUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationShowDAO.removeUserShowNotification(
      userId, airingId, providerChannelId
    ))

  def addOrGetUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Option[UserNotificationShow]] =
    db.run(UserNotificationShowDAO.addOrGetUserShowNotification(
      userId, airingId, providerChannelId, hash
    ))
}

object UserNotificationShowDAO {
  private[this] lazy val getUserShowNotificationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationShowTable
        .table
        .filter(_.userId === userId)
        .sortBy(_.name)
  }

  private[common] def getUserShowNotifications(userId: UUID): DBIO[Seq[UserNotificationShow]] =
    getUserShowNotificationsQuery(userId).result

  private[this] lazy val getNotificationsForShowAndProviderChannelQuery = Compiled {
    (airingId: Rep[String], providerChannelId: Rep[Long]) =>
      UserNotificationShowTable.table.filter { uNS =>
        uNS.airingId === airingId &&
        uNS.providerChannelId === providerChannelId
      }
  }

  private[common] def getNotificationsForShow(
    airingId: String,
    providerChannelId: Long
  ): DBIO[Seq[UserNotificationShow]] =
    getNotificationsForShowAndProviderChannelQuery(airingId, providerChannelId).result

  private[this] lazy val getUserShowNotificationQuery = Compiled {
    (userId: Rep[UUID], airingId: Rep[String], providerChannelId: Rep[Long]) =>
      UserNotificationShowTable.table.filter { uNS =>
        uNS.userId === userId &&
        uNS.airingId === airingId &&
        uNS.providerChannelId === providerChannelId
      }
  }

  private[common] def getUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long
  ): DBIO[Option[UserNotificationShow]] =
    getUserShowNotificationQuery(userId, airingId, providerChannelId).result.headOption

  private[common] def addUserShowNotification(
    notification: UserNotificationShow
  ): DBIO[UserNotificationShow] =
    (UserNotificationShowTable.table returning UserNotificationShowTable.table) += notification

  private[common] def removeUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] =
    UserNotificationShowTable.table.filter { uSN =>
      uSN.userId === userId &&
      uSN.airingId === airingId &&
      uSN.providerChannelId === providerChannelId
    }.delete.map(_ > 0)

  private[common] def addOrGetUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[Option[UserNotificationShow]] = for {
    exists <- getUserShowNotification(
      userId,
      airingId,
      providerChannelId
    )
    uSN <- exists
      .map(n => DBIO.successful(Some(n)))
      .getOrElse((for {
        channel <- ChannelDAO
          .getChannelForProviderChannel(providerChannelId)
        airing <- AiringDAO.getShowFromAiring(airingId)
      } yield (channel, airing)).flatMap {
        case (Some(channel), Some((show, series, sport))) => addUserShowNotification(UserNotificationShow(
          userId,
          airingId,
          providerChannelId,
          createShowNotificationName(show, series, sport),
          channel.callsign,
          hash
        )).map(Some(_))
        case _ => DBIO.successful(Option.empty)
      })
  } yield uSN

  private[this] def createShowNotificationName(
    show: Show,
    series: Option[Series],
    sport: Option[SportEvent]
  ): String = (show, series, sport) match {
    case (_, Some(sr), Some(sp)) =>
      s"${sr.seriesName}: ${sp.sportEventTitle}"
    case (sh, Some(sr), _) =>
      s"${sr.seriesName}: ${sh.title}"
    case (_, _, Some(sp)) => sp.sportEventTitle
    case (sh, _, _) => sh.title
  }
}
