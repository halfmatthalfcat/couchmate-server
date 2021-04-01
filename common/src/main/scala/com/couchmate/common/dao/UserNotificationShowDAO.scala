package com.couchmate.common.dao

import java.util.UUID
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data._
import com.couchmate.common.tables.UserNotificationShowTable
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object UserNotificationShowDAO {
  private[this] lazy val getUserShowNotificationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationShowTable
        .table
        .filter(_.userId === userId)
        .sortBy(_.name)
  }

  def getUserShowNotifications(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationShow]] =
    db.run(getUserShowNotificationsQuery(userId).result)


  private[this] lazy val getNotificationsForShowAndProviderChannelQuery = Compiled {
    (airingId: Rep[String], providerChannelId: Rep[Long]) =>
      UserNotificationShowTable.table.filter { uNS =>
        uNS.airingId === airingId &&
        uNS.providerChannelId === providerChannelId
      }
  }

  def getNotificationsForShow(
    airingId: String,
    providerChannelId: Long
  )(
    implicit
    db: Database
  ): Future[Seq[UserNotificationShow]] =
    db.run(getNotificationsForShowAndProviderChannelQuery(
      airingId,
      providerChannelId
    ).result)

  private[this] lazy val getUserShowNotificationQuery = Compiled {
    (userId: Rep[UUID], airingId: Rep[String], providerChannelId: Rep[Long]) =>
      UserNotificationShowTable.table.filter { uNS =>
        uNS.userId === userId &&
        uNS.airingId === airingId &&
        uNS.providerChannelId === providerChannelId
      }
  }

  def getUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long,
  )(
    implicit
    db: Database
  ): Future[Option[UserNotificationShow]] =
    db.run(getUserShowNotificationQuery(
      userId,
      airingId,
      providerChannelId
    ).result.headOption)

  def addUserShowNotification(
    notification: UserNotificationShow
  )(implicit db: Database): Future[UserNotificationShow] = db.run(
    (UserNotificationShowTable.table returning UserNotificationShowTable.table) += notification
  )


  def removeUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] = db.run(
    UserNotificationShowTable.table.filter { uSN =>
      uSN.userId === userId &&
      uSN.airingId === airingId &&
      uSN.providerChannelId === providerChannelId
    }.delete.map(_ > 0))

  def addOrGetUserShowNotification(
    userId: UUID,
    airingId: String,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[Option[UserNotificationShow]] = for {
    exists <- getUserShowNotification(
      userId,
      airingId,
      providerChannelId
    )
    uSN <- exists
      .map(n => Future.successful(Some(n)))
      .getOrElse((for {
        channel <- ChannelDAO
          .getChannelForProviderChannel(providerChannelId)
        airing <- AiringDAO.getShowFromAiring(airingId)
      } yield (channel, airing)).flatMap {
        case (Some(channel), Some(show)) => addUserShowNotification(UserNotificationShow(
          userId,
          airingId,
          providerChannelId,
          createShowNotificationName(show),
          channel.callsign,
          hash
        )).map(Some(_))
        case _ => Future.successful(Option.empty)
      })
  } yield uSN

  private[this] def createShowNotificationName(
    show: ShowDetailed
  ): String = show match {
    case ShowDetailed(_, _, _, Some(seriesName), Some(sportEventTitle), _) =>
      s"$seriesName: $sportEventTitle"
    case ShowDetailed(_, title, _, Some(seriesName), _, _) =>
      s"$seriesName: $title"
    case ShowDetailed(_, _, _, _, Some(sportEventTitle), _) => sportEventTitle
    case ShowDetailed(_, title, _, _, _, _) => title
  }
}
