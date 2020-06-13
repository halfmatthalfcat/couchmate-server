package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserMuteTable
import com.couchmate.data.models.UserMute
import slick.lifted.Compiled

import scala.concurrent.{ExecutionContext, Future}

trait UserMuteDAO {

  def getUserMutes(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[UUID]] =
    db.run(UserMuteDAO.getUserMutes(userId))

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

  private[db] def getUserMutes(userId: UUID): DBIO[Seq[UUID]] =
    getUserMutesQuery(userId).result

  private[db] def userIsMuted(userId: UUID, muteeId: UUID): DBIO[Option[UserMute]] =
    getUserMuteQuery(userId, muteeId).result.headOption

  private[db] def addUserMute(userMute: UserMute)(
    implicit
    ec: ExecutionContext
  ): DBIO[UserMute] = for {
    exists <- userIsMuted(userMute.userId, userMute.userMuteId)
    mute <- exists.fold[DBIO[UserMute]](
      (UserMuteTable.table returning UserMuteTable.table) += userMute
    )(DBIO.successful)
  } yield mute

  private[db] def removeUserMute(userMute: UserMute)(
    implicit
    ec: ExecutionContext
  ): DBIO[Boolean] = getUserMuteQuery(userMute.userId, userMute.userMuteId)
    .delete
    .map { deleted =>
      if (deleted > 0) true
      else false
    }
}