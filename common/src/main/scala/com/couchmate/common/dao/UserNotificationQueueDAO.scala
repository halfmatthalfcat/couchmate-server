package com.couchmate.common.dao

import java.time.{LocalDateTime, ZoneId}
import java.util.UUID
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data._
import com.couchmate.common.tables._
import com.typesafe.config.Config
import scalacache.caffeine.CaffeineCache
import scalacache.redis.RedisCache

import scala.concurrent.{ExecutionContext, Future}

object UserNotificationQueueDAO {
  private[this] lazy val getUserNotificationQueueItemQuery = Compiled {
    (notificationId: Rep[UUID]) =>
      UserNotificationQueueTable.table.filter(_.notificationId === notificationId)
  }

  def getUserNotificationQueueItem(notificationId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserNotificationQueueItem]] =
    db.run(getUserNotificationQueueItemQuery(notificationId).result.headOption)

  private[this] lazy val getUserNotificationQueueItemsForUserQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationQueueTable.table.filter(_.userId === userId)
  }

  def getUserNotificationQueueItemsForUser(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(getUserNotificationQueueItemsForUserQuery(userId).result)

  private[this] lazy val getUserNotificationQueueItemsForUserAndPlatformQuery = Compiled {
    (userId: Rep[UUID], platform: Rep[ApplicationPlatform]) =>
      UserNotificationQueueTable.table.filter { uNQ =>
        uNQ.userId === userId &&
        uNQ.platform === platform
      }
  }

  def getUserNotificationQueueItemsForUserAndPlatform(
    userId: UUID,
    platform: ApplicationPlatform
  )(implicit db: Database): Future[Seq[UserNotificationQueueItem]] =
    db.run(getUserNotificationQueueItemsForUserAndPlatformQuery(
      userId,
      platform
    ).result)

  private[this] lazy val getUserNotificationItemsForDeliveryQuery = Compiled {
    (deliverAt: Rep[LocalDateTime]) =>
      UserNotificationQueueTable.table.filter(_.deliverAt === deliverAt)
  }

  def getUserNotificationItemsForDelivery(deliverAt: LocalDateTime)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationQueueItem]] =
    db.run(getUserNotificationItemsForDeliveryQuery(deliverAt).result)

  private[this] lazy val getUserNotificationItemForUserAiringAndPlatformQuery = Compiled {
    (userId: Rep[UUID], airingId: Rep[String], platform: Rep[ApplicationPlatform], deliverAt: Rep[LocalDateTime]) =>
      UserNotificationQueueTable.table.filter { uNQ =>
        uNQ.userId === userId &&
        uNQ.airingId === airingId &&
        uNQ.platform === platform &&
        uNQ.deliverAt === deliverAt
      }
  }

  def getUserNotificationItemForUserAiringAndPlatform(
    userId: UUID,
    airingId: String,
    platform: ApplicationPlatform,
    deliverAt: LocalDateTime
  )(implicit db: Database): Future[Option[UserNotificationQueueItem]] =
    db.run(getUserNotificationItemForUserAiringAndPlatformQuery(
      userId, airingId, platform, deliverAt
    ).result.headOption)


  def getUserNotificationItemsForDeliveryRange(
    from: LocalDateTime, to: LocalDateTime
  )(implicit db: Database): Future[Seq[UserNotificationQueueItem]] =
    db.run(getUserNotificationItemsForDeliveryRangeQuery(from, to).result)

  private[this] lazy val getUserNotificationItemsForDeliveryRangeQuery = Compiled {
    (from: Rep[LocalDateTime], to: Rep[LocalDateTime]) =>
      UserNotificationQueueTable.table.filter { uNQ =>
        uNQ.deliverAt.between(from, to)
      }
  }

  def addUserNotificationsForShow(
    airingId: String,
    providerChannelId: Long,
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
    config: Config
  ): Future[Seq[UserNotificationQueueItem]] = for {
    airing <- AiringDAO.getAiring(airingId)()
    title <- getShowTitle(airing)
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    users <- airing.fold(Future.successful(Seq.empty[UserNotificationShow]))(
      _ => UserNotificationShowDAO.getNotificationsForShow(airingId, providerChannelId)
    )
    configurations <- Future.sequence(users.map(user => UserNotificationConfigurationDAO.getUserNotificationConfigurations(
      user.userId
    ))).map(_.fold(Seq.empty)(_ ++ _))
    notifications <- Future.sequence(configurations.map(configuration => addOrGetUserNotificationQueueItem(UserNotificationQueueItem(
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
    ))))
  } yield notifications

  def addUserNotificationForShow(
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
  ): Future[Seq[UserNotificationQueueItem]] = for {
    airing <- AiringDAO.getAiring(airingId)()
    title <- getShowTitle(airing)
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    configurations <- airing.fold(Future.successful(Seq.empty[UserNotificationConfiguration]))(
      _ => UserNotificationConfigurationDAO.getUserNotificationConfigurations(userId)
    )
    notifications <- Future.sequence(
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
      )))
    )
  } yield notifications

  def addUserNotificationsForEpisode(
    airingId: String,
    episodeId: Long,
    providerChannelId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
    config: Config
  ): Future[Seq[UserNotificationQueueItem]] = for {
    airing <- AiringDAO.getAiring(airingId)()
    title <- getShowTitle(airing)
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    users <- airing.fold(Future.successful(Seq.empty[UserNotificationSeries]))(
      a => UserNotificationSeriesDAO
        .getNotificationsForEpisode(episodeId, providerChannelId)
        .map(_.filter {
          case notification: UserNotificationSeries if notification.onlyNew => a.isNew
          case _ => true
        })
    )
    configurations <- Future.sequence(
      users.map(user => UserNotificationConfigurationDAO.getUserNotificationConfigurations(
        user.userId
      )),
    ).map(_.fold(Seq.empty)(_ ++ _))
    notifications <- Future.sequence(
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

  def removeUserNotificationForShow(
    userId: UUID,
    airingId: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Int] = db.run((for {
    n <- UserNotificationQueueTable.table if (
      n.airingId === airingId &&
      n.userId === userId &&
      n.notificationType === (UserNotificationQueueItemType.Show: UserNotificationQueueItemType)
    )
  } yield n).delete)

  def updateNotificationRead(notificationId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] = db.run((for {
    n <- UserNotificationQueueTable.table if n.notificationId === notificationId
  } yield (n.read, n.readAt))
    .update((true, Some(LocalDateTime.now(ZoneId.of("UTC")))))
    .map(_ > 0))

  def addUserNotificationForSeries(
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
  ): Future[Int] = for {
    series <- SeriesDAO.getSeries(seriesId)
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    airings <- series.fold(Future.successful(Seq.empty[(Airing, String)]))(
      _ => SeriesDAO.getUpcomingSeriesAirings(seriesId, providerChannelId).flatMap(results => Future.sequence(
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
        airingId = airing._1.airingId,
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
    notifications <- Future.sequence(n.map(addOrGetUserNotificationQueueItem))
  } yield notifications.size

  def removeUserNotificationForSeries(
    userId: UUID,
    seriesId: Long,
    providerChannelId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Int] = db.run(for {
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
  } yield n)

  def addUserNotificationsForSport(
    airingId: String,
    providerChannelId: Long,
    sportEventId: Long
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
    config: Config
  ): Future[Seq[UserNotificationQueueItem]] = for {
    teams <- SportEventTeamDAO.getSportEventTeams(sportEventId)
    airing <- AiringDAO.getAiring(airingId)()
    channel <- ChannelDAO.getChannelForProviderChannel(providerChannelId)
    title <- getShowTitle(airing)
    users <- airing.fold(Future.successful(Seq.empty[UserNotificationTeam]))(
      a => Future.sequence(teams.map(team => UserNotificationTeamDAO.getNotificationsForTeamAndProviderChannel(
        team.sportOrganizationTeamId,
        providerChannelId
      ))).map(_.fold(Seq.empty[UserNotificationTeam])(_ ++ _.filter {
        case notification: UserNotificationTeam if notification.onlyNew => a.isNew
        case _ => true
      })
    ))
    configurations <- Future.sequence(
      users.map(user => UserNotificationConfigurationDAO.getUserNotificationConfigurations(
        user.userId
      ))
    ).map(_.fold(Seq.empty[UserNotificationConfiguration])(_ ++ _))
    notifications <- Future.sequence(
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

  def removeUserNotificationForTeam(
    userId: UUID,
    sportOrganizationTeamId: Long,
    providerId: Long
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Int] = db.run(for {
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
  } yield n)

  def addUserNotificationForSportTeam(
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
  ): Future[Int] = for {
    team <- SportOrganizationTeamDAO.getSportOrganizationTeam(teamId)
    airings <- team.fold(Future.successful(Seq.empty[(Airing, String, Option[String])]))(
      _ => SportTeamDAO.getUpcomingSportTeamAirings(teamId, providerId).flatMap(results => Future.sequence(
        results.map(a2 => for {
          c <- ChannelDAO.getChannelForProviderAndAiring(
            providerId,
            a2.airingId
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
        airingId = airing._1.airingId,
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
    notifications <- Future.sequence(n.map(addOrGetUserNotificationQueueItem))
  } yield notifications.size

  def addOrGetUserNotificationQueueItem(item: UserNotificationQueueItem)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationQueueItem] = for {
    exists <- getUserNotificationItemForUserAiringAndPlatform(
      item.userId, item.airingId, item.applicationPlatform, item.deliverAt
    )
    uNQ <- exists.map(Future.successful).getOrElse(
      db.run((UserNotificationQueueTable.table returning UserNotificationQueueTable.table) += item)
    )
  } yield uNQ

  def deliverUserNotificationItem(
    notificationId: UUID,
    success: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] = for {
    exists <- getUserNotificationQueueItem(notificationId)
    notification <- exists.fold(Future.successful(false))(
      n => db.run((for {
        cUNQ <- UserNotificationQueueTable.table
          if cUNQ.notificationId === n.notificationId
      } yield (cUNQ.deliveredAt, cUNQ.success)).update((
        Some(LocalDateTime.now(ZoneId.of("UTC"))), success
      )).map(_ > 0)))
  } yield notification

  private[this] def getShowTitle(airing: Option[Airing])(
    implicit
    db: Database,
    ec: ExecutionContext,
    redis: RedisCache[String],
    caffeine: CaffeineCache[String],
  ): Future[String] = cache(
    "getShowTitle",
    airing.map(_.airingId).getOrElse("unknown")
  )(airing.fold(
    Future.successful("Show Reminder")
  )(a => for {
    s <- ShowDAO.getShow(a.showId)
    e <- s
      .map(_.episodeId)
      .map(eId => eId.fold(Future.successful(Option.empty[String]))(
        eId => SeriesDAO.getSeriesByEpisode(eId).map(_.map(_.seriesName))
      )).getOrElse(Future.successful(Option.empty[String]))
    sport <- s
      .map(_.sportEventId)
      .map(sEId => sEId.fold(Future.successful(Option.empty[String]))(
        sEId => SportEventDAO.getSportEvent(sEId)().map(_.map(_.sportEventTitle))
      )).getOrElse(Future.successful(Option.empty[String]))
  } yield {
    if (sport.nonEmpty) {
      sport.get
    } else if (e.nonEmpty) {
      e.get
    } else { s.map(_.title).getOrElse("Show Reminder") }
  }))()
}
