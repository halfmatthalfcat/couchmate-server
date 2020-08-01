package com.couchmate.common.dao

import java.util.UUID

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.api.user.{UserMute => ExternalUserMute}
import com.couchmate.common.models.data.UserMute
import com.couchmate.common.tables.{UserMetaTable, UserMuteTable}

import scala.concurrent.{ExecutionContext, Future}

trait UserMuteDAO {

  def getUserMutes(userId: UUID)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Seq[ExternalUserMute]] =
    db.run(UserMuteDAO.getUserMutes(userId).map(_.map((ExternalUserMute.apply _).tupled)))

  def addUserMute(userMute: UserMute)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserMute] =
    db.run(UserMuteDAO.addUserMute(userMute))

  def removeUserMute(userMute: UserMute)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[Boolean] =
    db.run(UserMuteDAO.removeUserMute(userMute))

}

object UserMuteDAO {
  private[this] lazy val getUserMuteQuery = Compiled {
    (userId: Rep[UUID], muteeId: Rep[UUID]) =>
      UserMuteTable.table.filter { user =>
        user.userId === userId &&
        user.userMuteId === muteeId
      }
  }

  private[this] lazy val getUserMutesQuery = Compiled { (userId: Rep[UUID]) =>
    UserMuteTable.table.filter(_.userId === userId).map(_.userMuteId)
  }

  private[common] def getUserMutes(userId: UUID)(
    implicit
    ec: ExecutionContext
  ): DBIO[Seq[(UUID, String)]] = (for {
    mutes <- UserMuteTable.table if mutes.userId === userId
    users <- UserMetaTable.table if users.userId === mutes.userMuteId
  } yield (users.userId, users.username)).result

  private[common] def userIsMuted(userId: UUID, muteeId: UUID): DBIO[Option[UserMute]] =
    getUserMuteQuery(userId, muteeId).result.headOption

  private[common] def addUserMute(userMute: UserMute)(
    implicit
    ec: ExecutionContext
  ): DBIO[UserMute] = for {
    exists <- userIsMuted(userMute.userId, userMute.userMuteId)
    mute <- exists.fold[DBIO[UserMute]](
      (UserMuteTable.table returning UserMuteTable.table) += userMute
    )(DBIO.successful)
  } yield mute

  private[common] def removeUserMute(userMute: UserMute)(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] = getUserMuteQuery(userMute.userId, userMute.userMuteId)
    .delete
    .map { deleted =>
      if (deleted > 0) true
      else false
    }
}