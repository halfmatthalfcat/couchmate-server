package com.couchmate.common.dao

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.UserMeta
import com.couchmate.common.tables.UserMetaTable

import scala.concurrent.{ExecutionContext, Future}

trait UserMetaDAO {

  def getUserMeta(userId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserMeta]] =
    db.run(UserMetaDAO.getUserMeta(userId))

  def getUserMeta$()(
    implicit
    session: SlickSession
  ): Flow[UUID, Option[UserMeta], NotUsed] =
    Slick.flowWithPassThrough(UserMetaDAO.getUserMeta)

  def emailExists(email: String)(
    implicit
    db: Database
  ): Future[Boolean] =
    db.run(UserMetaDAO.emailExists(email))

  def emailExists$()(
    implicit
    session: SlickSession
  ): Flow[String, Boolean, NotUsed] =
    Slick.flowWithPassThrough(UserMetaDAO.emailExists)

  def usernameExists(username: String)(
    implicit
    db: Database
  ): Future[Boolean] =
    db.run(UserMetaDAO.usernameExists(username))

  def usernameExists$()(
    implicit
    session: SlickSession
  ): Flow[String, Boolean, NotUsed] =
    Slick.flowWithPassThrough(UserMetaDAO.usernameExists)

  def upsertUserMeta(userMeta: UserMeta)(
    implicit
    ec: ExecutionContext,
    db: Database
  ): Future[UserMeta] =
    db.run(UserMetaDAO.upsertUserMeta(userMeta))

  def upsertUserMeta$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[UserMeta, UserMeta, NotUsed] =
    Slick.flowWithPassThrough(UserMetaDAO.upsertUserMeta)

}

object UserMetaDAO {
  private[this] lazy val getUserMetaQuery = Compiled { (userId: Rep[UUID]) =>
    UserMetaTable.table.filter(_.userId === userId)
  }

  private[common] def getUserMeta(userId: UUID): DBIO[Option[UserMeta]] =
    getUserMetaQuery(userId).result.headOption

  private[this] lazy val usernameExistsQuery = Compiled { (username: Rep[String]) =>
    UserMetaTable.table.filter(_.username === username).exists
  }

  private[common] def usernameExists(username: String): DBIO[Boolean] =
    usernameExistsQuery(username).result

  private[this] lazy val emailExistsQuery = Compiled { (email: Rep[String]) =>
    UserMetaTable.table.filter(_.email === email).exists
  }

  private[common] def emailExists(email: String): DBIO[Boolean] =
    emailExistsQuery(email).result

  private[common] def upsertUserMeta(userMeta: UserMeta)(
    implicit
    ec: ExecutionContext
  ): DBIO[UserMeta] = (for {
    exists <- getUserMeta(userMeta.userId)
    userMeta <- exists.fold[DBIO[UserMeta]](
      (UserMetaTable.table returning UserMetaTable.table) += userMeta
    )(_ => UserMetaTable.table.filter(_.userId === userMeta.userId).update(userMeta).map(_ => userMeta))
  } yield userMeta)
}
