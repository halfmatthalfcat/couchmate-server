package com.couchmate.data.db.dao

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{UserMetaTable, UserTable}
import com.couchmate.data.models.UserMeta

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
    db: Database
  ) = db.run(UserMetaDAO.upsertUserMeta(userMeta))

  def upsertUserMeta$()(
    implicit
    session: SlickSession
  ) = Slick.flowWithPassThrough(UserMetaDAO.upsertUserMeta)

}

object UserMetaDAO {
  private[this] lazy val getUserMetaQuery = Compiled { (userId: Rep[UUID]) =>
    UserMetaTable.table.filter(_.userId === userId)
  }

  private[db] def getUserMeta(userId: UUID): DBIO[Option[UserMeta]] =
    getUserMetaQuery(userId).result.headOption

  private[this] lazy val usernameExistsQuery = Compiled { (username: Rep[String]) =>
    UserMetaTable.table.filter(_.username === username).exists
  }

  private[db] def usernameExists(username: String): DBIO[Boolean] =
    usernameExistsQuery(username).result

  private[this] lazy val emailExistsQuery = Compiled { (email: Rep[String]) =>
    UserMetaTable.table.filter(_.email === email).exists
  }

  private[db] def emailExists(email: String): DBIO[Boolean] =
    emailExistsQuery(email).result

  private[db] def upsertUserMeta(userMeta: UserMeta) =
    (UserMetaTable.table returning UserMetaTable.table).insertOrUpdate(userMeta)
}
