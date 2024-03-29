package com.couchmate.common.dao

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.{User, UserExtType}
import com.couchmate.common.tables.{UserExtTable, UserMetaTable, UserTable}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait UserDAO {

  def getUser(userId: UUID)(
    implicit
    db: Database
  ): Future[Option[User]] =
    db.run(UserDAO.getUser(userId))

  def getUser$()(
    implicit
    session: SlickSession
  ): Flow[UUID, Option[User], NotUsed] =
    Slick.flowWithPassThrough(UserDAO.getUser)

  def getUserByEmail(email: String)(
    implicit
    db: Database
  ): Future[Option[User]] =
    db.run(UserDAO.getUserByEmail(email))

  def getUserByEmail$()(
    implicit
    session: SlickSession
  ): Flow[String, Option[User], NotUsed] =
    Slick.flowWithPassThrough(UserDAO.getUserByEmail)

  def getUserByExt(extType: UserExtType, extId: String)(
    implicit
    db: Database
  ): Future[Option[User]] =
    db.run(UserDAO.getUserByExt(extType, extId))

  def getUserByExt$()(
    implicit
    session: SlickSession
  ): Flow[(UserExtType, String), Option[User], NotUsed] =
    Slick.flowWithPassThrough(
      (UserDAO.getUserByExt _).tupled
    )

  def upsertUser(user: User)(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[User] =
    db.run(UserDAO.upsertUser(user))

  def upsertUser$()(
    implicit
    ec: ExecutionContext,
    session: SlickSession
  ): Flow[User, User, NotUsed] =
    Slick.flowWithPassThrough(UserDAO.upsertUser)
}

object UserDAO {
  private[this] lazy val getUserQuery = Compiled { (userId: Rep[UUID]) =>
    UserTable.table.filter(_.userId === userId)
  }

  private[common] def getUser(userId: UUID): DBIO[Option[User]] =
    getUserQuery(userId).result.headOption

  private[this] lazy val getUserByEmailQuery = Compiled { (email: Rep[String]) =>
    for {
      u <- UserTable.table
      um <- UserMetaTable.table if (
        u.userId === um.userId &&
        um.email === email
      )
    } yield u
  }

  private[common] def getUserByEmail(email: String): DBIO[Option[User]] =
    getUserByEmailQuery(email).result.headOption

  private[this] lazy val getUserByExtQuery = Compiled {
    (extType: Rep[UserExtType], extId: Rep[String]) =>
      for {
        u <- UserTable.table
        ue <- UserExtTable.table if (
          ue.extType === extType &&
          ue.extId === extId
        )
      } yield u
  }

  private[common] def getUserByExt(
    extType: UserExtType,
    extId: String
  ): DBIO[Option[User]] =
    getUserByExtQuery(extType, extId).result.headOption

  private[common] def upsertUser(user: User)(
    implicit
    ec: ExecutionContext
  ): DBIO[User] =
    user.userId.fold[DBIO[User]](
      (UserTable.table returning UserTable.table) += user.copy(userId = Some(UUID.randomUUID()))
    ) { (userId: UUID) => for {
      _ <- UserTable
        .table
        .filter(_.userId === userId)
        .update(user)
      updated <- UserDAO.getUser(userId)
    } yield updated.get}
 }
