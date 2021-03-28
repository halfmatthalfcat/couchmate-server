package com.couchmate.common.dao

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ApplicationPlatform, UserNotificationConfiguration}
import com.couchmate.common.tables.UserNotificationConfigurationTable

import scala.concurrent.{ExecutionContext, Future}

object UserNotificationConfigurationDAO {
  private[this] lazy val getUserNotificationConfigurationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationConfigurationTable.table.filter(_.userId === userId)
  }

  def getUserNotificationConfigurations(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationConfiguration]] =
    db.run(getUserNotificationConfigurationsQuery(userId).result)

  private[this] val getUserNotificationConfigurationForUserAndDeviceQuery = Compiled {
    (userId: Rep[UUID], deviceId: Rep[String]) =>
      UserNotificationConfigurationTable.table.filter { uNC =>
        uNC.userId === userId &&
        uNC.deviceId === deviceId
      }
  }

  private[common] def getUserNotificationConfigurationForUserAndDevice(
    userId: UUID,
    deviceId: String
  )(implicit db: Database): Future[Option[UserNotificationConfiguration]] = db.run(
    getUserNotificationConfigurationForUserAndDeviceQuery(userId, deviceId).result.headOption
  )

  private[this] lazy val getActiveUserNotificationConfigurationsForPlatformQuery = Compiled {
    (app: Rep[ApplicationPlatform]) =>
      UserNotificationConfigurationTable.table.filter { un =>
        un.active && un.platform === app
      }
  }

  def getActiveUserNotificationConfigurationsForPlatform(app: ApplicationPlatform)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationConfiguration]] =
    db.run(getActiveUserNotificationConfigurationsForPlatformQuery(app).result)

  private[this] lazy val getUserNotificationConfigurationQuery = Compiled {
    (userId: Rep[UUID], app: Rep[ApplicationPlatform], deviceId: Rep[Option[String]]) =>
      UserNotificationConfigurationTable.table.filter { un =>
        un.userId === userId &&
        un.platform === app &&
        un.deviceId === deviceId
      }
  }

  def getUserNotificationConfiguration(
    userId: UUID,
    platform: ApplicationPlatform,
    deviceId: Option[String]
  )(
    implicit
    db: Database
  ): Future[Option[UserNotificationConfiguration]] =
    db.run(getUserNotificationConfigurationQuery(
      userId,
      platform,
      deviceId
    ).result.headOption)

  def updateUserNotificationActive(
    userId: UUID,
    os: ApplicationPlatform,
    deviceId: Option[String],
    active: Boolean
  )(implicit db: Database): Future[Int] = db.run((for {
    configurations <- UserNotificationConfigurationTable.table if (
      configurations.userId === userId &&
      configurations.platform === os &&
      configurations.deviceId === deviceId
    )
  } yield configurations.active).update(active))

  def updateUserNotificationToken(
    userId: UUID,
    platform: ApplicationPlatform,
    deviceId: Option[String],
    token: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] = for {
    n <- getUserNotificationConfiguration(
      userId, platform, deviceId
    )
    newNotification <- n.fold(for {
      _ <- db.run((UserNotificationConfigurationTable.table returning UserNotificationConfigurationTable.table) += UserNotificationConfiguration(
        userId = userId,
        platform = platform,
        deviceId = deviceId,
        active = false,
        token = Some(token)
      ))
    } yield true)(_ => db.run(UserNotificationConfigurationTable.table.filter { uNC =>
      uNC.userId === userId &&
      uNC.platform === platform &&
      uNC.deviceId === deviceId
    }.map(_.token)
     .update(Some(token))
     .map(_ > 0)))
  } yield newNotification

  private[common] def upsertUserNotificationConfiguration(notification: UserNotificationConfiguration)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationConfiguration] = for {
    n <- getUserNotificationConfiguration(
      notification.userId,
      notification.platform,
      notification.deviceId
    )
    newNotification <- n.fold(
      db.run((UserNotificationConfigurationTable.table returning UserNotificationConfigurationTable.table) += notification)
    )(_ => db.run(UserNotificationConfigurationTable.table.filter { uNC =>
      uNC.userId === notification.userId &&
      uNC.platform === notification.platform &&
      uNC.deviceId === notification.deviceId
    }.map(uNC => (uNC.active, uNC.token))
     .update((notification.active, notification.token))
     .map(_ => notification)))
  } yield newNotification
}