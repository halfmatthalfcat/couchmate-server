package com.couchmate.common.dao

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ApplicationPlatform, UserNotificationConfiguration}
import com.couchmate.common.tables.UserNotificationConfigurationTable

import scala.concurrent.{ExecutionContext, Future}

trait UserNotificationConfigurationDAO {

  def getUserNotificationConfigurations(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationConfiguration]] =
    db.run(UserNotificationConfigurationDAO.getUserNotificationConfigurations(userId))

  def getUserNotificationConfiguration(
    userId: UUID,
    platform: ApplicationPlatform,
    deviceId: Option[String]
  )(
    implicit
    db: Database
  ): Future[Option[UserNotificationConfiguration]] =
    db.run(UserNotificationConfigurationDAO.getUserNotificationConfiguration(
      userId, platform, deviceId
    ))

  def getActiveUserNotificationConfigurationsForPlatform(app: ApplicationPlatform)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationConfiguration]] =
    db.run(UserNotificationConfigurationDAO.getActiveUserNotificationConfigurationsForPlatform(
      app
    ))

  def upsertUserNotificationConfiguration(configuration: UserNotificationConfiguration)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationConfiguration] =
    db.run(UserNotificationConfigurationDAO.upsertUserNotificationConfiguration(configuration))

  def updateUserNotificationToken(
    userId: UUID,
    platform: ApplicationPlatform,
    deviceId: Option[String],
    token: String
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserNotificationConfigurationDAO.updateUserNotificationToken(
      userId, platform, deviceId, token
    ))

  def updateUserNotificationActive(
    userId: UUID,
    platform: ApplicationPlatform,
    deviceId: Option[String],
    active: Boolean
  )(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Int] =
    db.run(UserNotificationConfigurationDAO.updateUserNotificationActive(
      userId, platform, deviceId, active
    ))

}

object UserNotificationConfigurationDAO {
  private[this] lazy val getUserNotificationConfigurationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationConfigurationTable.table.filter(_.userId === userId)
  }

  private[common] def getUserNotificationConfigurations(userId: UUID): DBIO[Seq[UserNotificationConfiguration]] =
    getUserNotificationConfigurationsQuery(userId).result

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
  ): DBIO[Option[UserNotificationConfiguration]] =
    getUserNotificationConfigurationForUserAndDeviceQuery(userId, deviceId).result.headOption

  private[this] lazy val getActiveUserNotificationConfigurationsForPlatformQuery = Compiled {
    (app: Rep[ApplicationPlatform]) =>
      UserNotificationConfigurationTable.table.filter { un =>
        un.active && un.platform === app
      }
  }

  private[common] def getActiveUserNotificationConfigurationsForPlatform(app: ApplicationPlatform): DBIO[Seq[UserNotificationConfiguration]] =
    getActiveUserNotificationConfigurationsForPlatformQuery(app).result

  private[this] lazy val getUserNotificationConfigurationQuery = Compiled {
    (userId: Rep[UUID], app: Rep[ApplicationPlatform], deviceId: Rep[Option[String]]) =>
      UserNotificationConfigurationTable.table.filter { un =>
        un.userId === userId &&
        un.platform === app &&
        un.deviceId === deviceId
      }
  }

  private[common] def updateUserNotificationActive(
    userId: UUID,
    os: ApplicationPlatform,
    deviceId: Option[String],
    active: Boolean
  ) = (for {
    configurations <- UserNotificationConfigurationTable.table if (
      configurations.userId === userId &&
      configurations.platform === os &&
      configurations.deviceId === deviceId
    )
  } yield configurations.active).update(active)

  private[common] def getUserNotificationConfiguration(
    userId: UUID,
    app: ApplicationPlatform,
    deviceId: Option[String]
  ): DBIO[Option[UserNotificationConfiguration]] =
    getUserNotificationConfigurationQuery(userId, app, deviceId).result.headOption

  private[common] def updateUserNotificationToken(
    userId: UUID,
    platform: ApplicationPlatform,
    deviceId: Option[String],
    token: String
  )(implicit ec: ExecutionContext): DBIO[Boolean] = for {
    n <- getUserNotificationConfiguration(
      userId, platform, deviceId
    )
    newNotification <- n.fold[DBIO[Boolean]](for {
      _ <- (UserNotificationConfigurationTable.table returning UserNotificationConfigurationTable.table) += UserNotificationConfiguration(
        userId = userId,
        platform = platform,
        deviceId = deviceId,
        active = false,
        token = Some(token)
      )
    } yield true)(_ => UserNotificationConfigurationTable.table.filter { uNC =>
      uNC.userId === userId &&
      uNC.platform === platform &&
      uNC.deviceId === deviceId
    }.map(_.token)
     .update(Some(token))
     .map(_ > 0))
  } yield newNotification

  private[common] def upsertUserNotificationConfiguration(notification: UserNotificationConfiguration)(
    implicit
    ec: ExecutionContext
  ): DBIO[UserNotificationConfiguration] = for {
    n <- getUserNotificationConfiguration(
      notification.userId,
      notification.platform,
      notification.deviceId
    )
    newNotification <- n.fold[DBIO[UserNotificationConfiguration]](
      (UserNotificationConfigurationTable.table returning UserNotificationConfigurationTable.table) += notification
    )(_ => UserNotificationConfigurationTable.table.filter { uNC =>
      uNC.userId === notification.userId &&
      uNC.platform === notification.platform &&
      uNC.deviceId === notification.deviceId
    }.map(uNC => (uNC.active, uNC.token))
     .update((notification.active, notification.token))
     .map(_ => notification))
  } yield newNotification
}