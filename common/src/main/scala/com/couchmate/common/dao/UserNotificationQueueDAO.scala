package com.couchmate.common.dao

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data._
import com.couchmate.common.tables._
import com.typesafe.config.Config

import scala.concurrent.{ExecutionContext, Future}

trait UserNotificationQueueDAO {

  def getUserNotificationQueueItem(notificationId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.getUserNotificationQueueItem(notificationId))

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

  def getUserNotificationItemsForDelivery(deliveryAt: LocalDateTime)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.getUserNotificationItemsForDelivery(deliveryAt))

  def getUserNotificationItemsForDeliveryRange(
    from: LocalDateTime, to: LocalDateTime
  )(implicit db: Database): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.getUserNotificationItemsForDeliveryRange(from, to))

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
    notificationId: UUID,
    success: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationQueueDAO.deliverUserNotificationItem(
      notificationId, success
    ))

  def addUserNotificationsForShow(
    airingId: String,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    config: Config
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationsForShow(
      airingId, providerChannelId
    ))

  def addUserNotificationForShow(
    userId: UUID,
    airingId: String,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationForShow(
      userId, airingId, providerChannelId, hash
    ))

  def addUserNotificationsForEpisode(
    airingId: String,
    episodeId: Long,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    config: Config
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationsForEpisode(
      airingId, episodeId, providerChannelId
    ))

  def addUserNotificationForSeries(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Int] =
    db.run(UserNotificationQueueDAO.addUserNotificationForSeries(
      userId, seriesId, providerChannelId, hash, onlyNew
    ))

  def addUserNotificationsForSport(
    airingId: String,
    providerChannelId: Long,
    sportEventId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    config: Config
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(UserNotificationQueueDAO.addUserNotificationsForSport(
      airingId, providerChannelId, sportEventId
    ))

  def addUserNotificationForSportTeam(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Int] =
    db.run(UserNotificationQueueDAO.addUserNotificationForSportTeam(
      userId, teamId, providerId, hash, onlyNew
    ))

  def updateNotificationRead(notificationId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationQueueDAO.updateNotificationRead(notificationId))

  def removeUserNotificationForShow(
    userId: UUID,
    airingId: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Int] =
    db.run(UserNotificationQueueDAO.removeUserNotificationForShow(userId, airingId))

  def removeUserNotificationForSeries(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Int] =
    db.run(UserNotificationQueueDAO.removeUserNotificationForSeries(
      userId, seriesId, providerChannelId
    ))

  def removeUserNotificationForTeam(
    userId: UUID,
    sportOrganizationTeamId: Long,
    providerId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Int] =
    db.run(UserNotificationQueueDAO.removeUserNotificationForTeam(
      userId, sportOrganizationTeamId, providerId
    ))
}

object UserNotificationQueueDAO {
  private[this] lazy val getUserNotificationQueueItemQuery = Compiled {
    (notificationId: Rep[UUID]) =>
      UserNotificationQueueTable.table.filter(_.notificationId === notificationId)
  }

  private[common] def getUserNotificationQueueItem(notificationId: UUID): DBIO[Option[UserNotificationQueueItem]] =
    getUserNotificationQueueItemQuery(notificationId).result.headOption

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

  private[this] lazy val getUserNotificationItemsForDeliveryQuery = Compiled {
    (deliverAt: Rep[LocalDateTime]) =>
      UserNotificationQueueTable.table.filter(_.deliverAt === deliverAt)
  }

  private[common] def getUserNotificationItemsForDelivery(deliverAt: LocalDateTime): DBIO[Seq[UserNotificationQueueItem]] =
    getUserNotificationItemsForDeliveryQuery(deliverAt).result

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

  private[this] lazy val getUserNotificationItemsForDeliveryRangeQuery = Compiled {
    (from: Rep[LocalDateTime], to: Rep[LocalDateTime]) =>
      UserNotificationQueueTable.table.filter { uNQ =>
        uNQ.deliverAt.between(from, to)
      }
  }

  private[common] def getUserNotificationItemsForDeliveryRange(
    from: LocalDateTime, to: LocalDateTime
  ): DBIO[Seq[UserNotificationQueueItem]] =
    getUserNotificationItemsForDeliveryRangeQuery(from, to).result

  private[common] def addUserNotificationsForShow(
    airingId: String,
    providerChannelId: Long,
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    config: Config
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    airing <- AiringDAO.getAiring(airingId)
    title <- getShowTitle(airing)
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    users <- airing.fold[DBIO[Seq[UserNotificationShow]]](DBIO.successful(Seq.empty[UserNotificationShow]))(
      _ => UserNotificationShowDAO.getNotificationsForShow(airingId, providerChannelId)
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
        notificationType = UserNotificationQueueItemType.Show,
        hash = users
          .find(_.userId == configuration.userId)
          .map(_.hash)
          .getOrElse(config.getString("features.room.default")),
        title = title,
        callsign = channel.map(_.callsign),
        applicationPlatform = configuration.platform,
        token = configuration.token,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.get.startTime.minusMinutes(15)
      )))
    )
  } yield notifications

  private[common] def addUserNotificationForShow(
    userId: UUID,
    airingId: String,
    providerChannelId: Long,
    hash: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    airing <- AiringDAO.getAiring(airingId)
    title <- getShowTitle(airing)
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    configurations <- airing.fold[DBIO[Seq[UserNotificationConfiguration]]](DBIO.successful(Seq.empty))(
      _ => UserNotificationConfigurationDAO.getUserNotificationConfigurations(userId)
    )
    notifications <- DBIO.sequence(
      configurations.map(configuration => addOrGetUserNotificationQueueItem(UserNotificationQueueItem(
        notificationId = UUID.randomUUID(),
        userId = configuration.userId,
        airingId = airingId,
        notificationType = UserNotificationQueueItemType.Show,
        hash = hash,
        title = title,
        callsign = channel.map(_.callsign),
        applicationPlatform = configuration.platform,
        token = configuration.token,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.get.startTime.minusMinutes(15)
      ))))
  } yield notifications

  private[common] def addUserNotificationsForEpisode(
    airingId: String,
    episodeId: Long,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    config: Config
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    airing <- AiringDAO.getAiring(airingId)
    title <- getShowTitle(airing)
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    users <- airing.fold[DBIO[Seq[UserNotificationSeries]]](DBIO.successful(Seq.empty))(
      a => UserNotificationSeriesDAO
        .getNotificationsForEpisode(episodeId, providerChannelId)
        .map(_.filter {
          case notification: UserNotificationSeries if notification.onlyNew => a.isNew
          case _ => true
        })
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
        notificationType = UserNotificationQueueItemType.Episode,
        hash = users
          .find(_.userId == configuration.userId)
          .map(_.hash)
          .getOrElse(config.getString("features.room.default")),
        title = title,
        callsign = channel.map(_.callsign),
        applicationPlatform = configuration.platform,
        token = configuration.token,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.get.startTime.minusMinutes(15)
      )))
    )
  } yield notifications

  private[common] def removeUserNotificationForShow(
    userId: UUID,
    airingId: String
  )(implicit ec: ExecutionContext): DBIO[Int] = (for {
    n <- UserNotificationQueueTable.table if (
      n.airingId === airingId &&
      n.userId === userId &&
      n.notificationType === (UserNotificationQueueItemType.Show: UserNotificationQueueItemType)
    )
  } yield n).delete

  private[common] def updateNotificationRead(notificationId: UUID)(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] = (for {
    n <- UserNotificationQueueTable.table if n.notificationId === notificationId
  } yield (n.read, n.readAt))
    .update((true, Some(LocalDateTime.now(ZoneId.of("UTC")))))
    .map(_ > 0)

  private[common] def addUserNotificationForSeries(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[Int] = for {
    series <- SeriesDAO.getSeries(seriesId)
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    airings <- series.fold[DBIO[Seq[(Airing, String)]]](DBIO.successful(Seq.empty))(
      _ => SeriesDAO.getUpcomingSeriesAirings(seriesId, providerChannelId).flatMap(results => DBIO.sequence(
        results.map(a2 => getShowTitle(Some(a2)).map(title => (a2, title)))
      ))
    )
    configurations <- UserNotificationConfigurationDAO.getUserNotificationConfigurations(userId)
    n = airings.filter {
      case (airing: Airing, _) if onlyNew => airing.isNew
      case _ => true
    }.foldLeft[Seq[UserNotificationQueueItem]](Seq.empty)((acc, airing) => acc ++ configurations.map(
      configuration => UserNotificationQueueItem(
        notificationId = UUID.randomUUID(),
        userId = configuration.userId,
        airingId = airing._1.airingId.get,
        notificationType = UserNotificationQueueItemType.Episode,
        hash = hash,
        title = airing._2,
        callsign = channel.map(_.callsign),
        applicationPlatform = configuration.platform,
        token = configuration.token,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing._1.startTime.minusMinutes(15)
      )
    ))
    notifications <- DBIO.sequence(n.map(addOrGetUserNotificationQueueItem))
  } yield notifications.size

  private[common] def removeUserNotificationForSeries(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long
  )(implicit ec: ExecutionContext): DBIO[Int] = for {
    a <- (for {
      e <- EpisodeTable.table if e.seriesId === seriesId
      s <- ShowTable.table if s.episodeId === e.episodeId
      a <- AiringTable.table if (
        a.showId === s.showId &&
        a.startTime >= LocalDateTime.now(ZoneId.of("UTC"))
      )
      l <- LineupTable.table if (
        a.airingId === l.airingId &&
        l.providerChannelId === providerChannelId
      )
    } yield a.airingId).result
    n <- UserNotificationQueueTable.table.filter { uNQ =>
      uNQ.airingId.inSetBind(a) &&
      uNQ.userId === userId &&
      uNQ.notificationType === (UserNotificationQueueItemType.Episode: UserNotificationQueueItemType) &&
      uNQ.deliverAt >= LocalDateTime.now(ZoneId.of("UTC"))
    }.delete
  } yield n

  private[common] def addUserNotificationsForSport(
    airingId: String,
    providerChannelId: Long,
    sportEventId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database,
    config: Config
  ): DBIO[Seq[UserNotificationQueueItem]] = for {
    teams <- SportEventTeamDAO.getSportEventTeams(sportEventId)
    airing <- AiringDAO.getAiring(airingId)
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    title <- getShowTitle(airing)
    users <- airing.fold[DBIO[Seq[UserNotificationTeam]]](DBIO.successful(Seq.empty))(
      a => DBIO.fold(
        teams.map(team => UserNotificationTeamDAO.getNotificationsForTeamAndProviderChannel(
          team.sportOrganizationTeamId,
          providerChannelId
        )),
        Seq.empty
      )(_ ++ _.filter {
        case notification: UserNotificationTeam if notification.onlyNew => a.isNew
        case _ => true
      })
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
        notificationType = UserNotificationQueueItemType.Team,
        hash = users
          .find(_.userId == configuration.userId)
          .map(_.hash)
          .getOrElse(config.getString("features.room.default")),
        title = title,
        callsign = channel.map(_.callsign),
        applicationPlatform = configuration.platform,
        token = configuration.token,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing.get.startTime.minusMinutes(15)
      )))
    )
  } yield notifications

  private[common] def removeUserNotificationForTeam(
    userId: UUID,
    sportOrganizationTeamId: Long,
    providerId: Long
  )(implicit ec: ExecutionContext): DBIO[Int] = for {
    a <- (for {
      sET <- SportEventTeamTable.table if sET.sportOrganizationTeamId === sportOrganizationTeamId
      s <- ShowTable.table if s.sportEventId === sET.sportEventId
      a <- AiringTable.table if (
        a.showId === s.showId &&
        a.startTime >= LocalDateTime.now(ZoneId.of("UTC"))
      )
      l <- LineupTable.table if l.airingId === a.airingId
      pc <- ProviderChannelTable.table if pc.providerChannelId === l.providerChannelId
      p <- ProviderTable.table if (
        p.providerId === pc.providerId &&
        p.providerId === providerId
      )
    } yield a.airingId).result
    n <- UserNotificationQueueTable.table.filter { uNQ =>
      uNQ.airingId.inSetBind(a) &&
      uNQ.userId === userId &&
      uNQ.notificationType === (UserNotificationQueueItemType.Team: UserNotificationQueueItemType) &&
      uNQ.deliverAt >= LocalDateTime.now(ZoneId.of("UTC"))
    }.delete
  } yield n

  private[common] def addUserNotificationForSportTeam(
    userId: UUID,
    teamId: Long,
    providerId: Long,
    hash: String,
    onlyNew: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[Int] = for {
    team <- SportOrganizationTeamDAO.getSportOrganizationTeam(teamId)
    airings <- team.fold[DBIO[Seq[(Airing, String, Option[String])]]](DBIO.successful(Seq.empty))(
      _ => SportTeamDAO.getUpcomingSportTeamAirings(teamId, providerId).flatMap(results => DBIO.sequence(
        results.map(a2 => for {
          c <- ChannelDAO.getChannelForProviderAndAiring(
            providerId,
            a2.airingId.get
          )
          title <- getShowTitle(Some(a2))
        } yield (a2, title, c.map(_.callsign)))
      ))
    )
    configurations <- UserNotificationConfigurationDAO.getUserNotificationConfigurations(userId)
    n = airings.filter {
      case (airing: Airing, _, _) if onlyNew => airing.isNew
      case _ => true
    }.foldLeft[Seq[UserNotificationQueueItem]](Seq.empty)((acc, airing) => acc ++ configurations.map(
      configuration => UserNotificationQueueItem(
        notificationId = UUID.randomUUID(),
        userId = configuration.userId,
        airingId = airing._1.airingId.get,
        notificationType = UserNotificationQueueItemType.Team,
        hash = hash,
        title = airing._2,
        callsign = airing._3,
        applicationPlatform = configuration.platform,
        token = configuration.token,
        // This should never throw because if there is no airing, we dont return any users
        // and in turn, no configurations and we never reach this point
        deliverAt = airing._1.startTime.minusMinutes(15)
      )
    ))
    notifications <- DBIO.sequence(n.map(addOrGetUserNotificationQueueItem))
  } yield notifications.size

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
    notificationId: UUID,
    success: Boolean
  )(implicit ec: ExecutionContext): DBIO[Boolean] = for {
    exists <- getUserNotificationQueueItem(notificationId)
    notification <- exists.fold[DBIO[Boolean]](DBIO.successful(false))(
      n => (for {
        cUNQ <- UserNotificationQueueTable.table
          if cUNQ.notificationId === n.notificationId
      } yield (cUNQ.deliveredAt, cUNQ.success)).update((
        Some(LocalDateTime.now(ZoneId.of("UTC"))), success
      )).map(_ > 0))
  } yield notification

  private[this] def getShowTitle(airing: Option[Airing])(
    implicit
    ec: ExecutionContext,
    db: Database
  ): DBIO[String] = airing.fold[DBIO[String]](
    DBIO.successful("Show Reminder")
  )(a => for {
    s <- ShowDAO.getShow(a.showId)
    e <- s
      .map(_.episodeId)
      .map(eId => eId.fold[DBIO[Option[String]]](DBIO.successful(Option.empty))(
        eId => SeriesDAO.getSeriesByEpisode(eId).map(_.map(_.seriesName))
      )).getOrElse(DBIO.successful(Option.empty))
    sport <- s
      .map(_.sportEventId)
      .map(sEId => sEId.fold[DBIO[Option[String]]](DBIO.successful(Option.empty))(
        sEId => SportEventDAO.getSportEvent(sEId).map(_.map(_.sportEventTitle))
      )).getOrElse(DBIO.successful(Option.empty))
  } yield {
    if (sport.nonEmpty) {
      sport.get
    } else if (e.nonEmpty) {
      e.get
    } else { s.map(_.title).getOrElse("Show Reminder") }
  })
}
