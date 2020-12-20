package com.couchmate.common.dao

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{ApplicationPlatform, UserNotificationConfiguration}
import com.couchmate.common.tables.UserNotificationConfigurationTable

import scala.concurrent.{ExecutionContext, Future}

trait UserNotificationConfigurationDAO {

  def getUserNotifications(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserNotificationConfiguration]] =
    db.run(UserNotificationConfigurationDAO.getUserNotificationConfigurations(userId))

  def getUserNotificationConfiguration(
    userId: UUID,
    platform: ApplicationPlatform
  )(
    implicit
    db: Database
  ): Future[Option[UserNotificationConfiguration]] =
    db.run(UserNotificationConfigurationDAO.getUserNotificationConfiguration(
      userId, platform
    ))

  def upsertUserNotificationConfiguration(configuration: UserNotificationConfiguration)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserNotificationConfiguration] =
    db.run(UserNotificationConfigurationDAO.upsertUserNotificationConfiguration(configuration))

}

object UserNotificationConfigurationDAO {
  private[this] lazy val getUserNotificationConfigurationsQuery = Compiled {
    (userId: Rep[UUID]) =>
      UserNotificationConfigurationTable.table.filter(_.userId === userId)
  }

  private[common] def getUserNotificationConfigurations(userId: UUID): DBIO[Seq[UserNotificationConfiguration]] =
    getUserNotificationConfigurationsQuery(userId).result

  private[this] lazy val getUserNotificationConfigurationQuery = Compiled {
    (userId: Rep[UUID], app: Rep[ApplicationPlatform]) =>
      UserNotificationConfigurationTable.table.filter { un =>
        un.userId === userId &&
        un.platform === app
      }
  }

  private[common] def getUserNotificationConfiguration(userId: UUID, app: ApplicationPlatform): DBIO[Option[UserNotificationConfiguration]] =
    getUserNotificationConfigurationQuery(userId, app).result.headOption

  private[common] def upsertUserNotificationConfiguration(notification: UserNotificationConfiguration)(
    implicit
    ec: ExecutionContext
  ): DBIO[UserNotificationConfiguration] =
    (UserNotificationConfigurationTable.table returning UserNotificationConfigurationTable.table) += notification
}