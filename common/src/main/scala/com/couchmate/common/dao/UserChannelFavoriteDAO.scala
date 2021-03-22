package com.couchmate.common.dao

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserChannelFavorite
import com.couchmate.common.tables.UserChannelFavoriteTable

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait UserChannelFavoriteDAO {
  def getUserChannelFavorites(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UserChannelFavorite]] =
    db.run(UserChannelFavoriteDAO.getUserChannelFavorites(userId))

  def getUserChannelFavorite(userId: UUID, providerChannelId: Long)(
    implicit
    db: Database
  ): Future[Option[UserChannelFavorite]] =
    db.run(UserChannelFavoriteDAO.getUserChannelFavorite(userId, providerChannelId))

  def addUserChannelFavorite(userId: UUID, providerChannelId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[UserChannelFavorite] =
    db.run(UserChannelFavoriteDAO.addUserChannelFavorite(userId, providerChannelId))

  def removeUserChannelFavorite(userId: UUID, providerChannelId: Long)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[Boolean] =
    db.run(UserChannelFavoriteDAO.removeUserChannelFavorite(userId, providerChannelId))
}

object UserChannelFavoriteDAO {
  private[this] lazy val getUserChannelFavoritesQuery = Compiled {
    (userId: Rep[UUID]) => UserChannelFavoriteTable.table.filter(_.userId === userId)
  }

  private[couchmate] def getUserChannelFavorites(userId: UUID): DBIO[Seq[UserChannelFavorite]] =
    getUserChannelFavoritesQuery(userId).result

  private[this] lazy val getUserChannelFavoriteQuery = Compiled {
    (userId: Rep[UUID], providerChannelId: Rep[Long]) => UserChannelFavoriteTable.table.filter { uCF =>
      uCF.userId === userId &&
      uCF.providerChannelId === providerChannelId
    }
  }

  private[couchmate] def getUserChannelFavorite(userId: UUID, providerChannelId: Long): DBIO[Option[UserChannelFavorite]] =
    getUserChannelFavoriteQuery(userId, providerChannelId).result.headOption

  private[couchmate] def addUserChannelFavorite(
    userId: UUID,
    providerChannelId: Long
  )(implicit ec: ExecutionContext): DBIO[UserChannelFavorite] = for {
    exists <- getUserChannelFavorite(userId, providerChannelId)
    userChannelFavorite <- exists.fold[DBIO[UserChannelFavorite]](
      (UserChannelFavoriteTable.table returning UserChannelFavoriteTable.table) += UserChannelFavorite(
        userId, providerChannelId
      )
    )(DBIO.successful)
  } yield userChannelFavorite

  private[couchmate] def removeUserChannelFavorite(
    userId: UUID,
    providerChannelId: Long,
  )(implicit ec: ExecutionContext): DBIO[Boolean] =
    getUserChannelFavoriteQuery(userId, providerChannelId).delete.map(_ > 1)
}
