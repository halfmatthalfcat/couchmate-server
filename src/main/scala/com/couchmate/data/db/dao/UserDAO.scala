package com.couchmate.data.db.dao

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{UserExtTable, UserMetaTable, UserTable}
import com.couchmate.data.models.{User, UserExtType, UserMeta, UserPrivate, UserProvider, UserRole}
import com.github.t3hnar.bcrypt._

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

  def createEmailAccount(
    email: String,
    username: String,
    password: String,
    zipCode: String,
    providerId: Long,
  )(
    implicit
    db: Database,
    ec: ExecutionContext
  ): Future[User] =
    db.run(UserDAO.createEmailAccount(
      email,
      username,
      password,
      zipCode,
      providerId
    ))

  def createEmailAccount$()(
    implicit
    session: SlickSession,
    ec: ExecutionContext
  ): Flow[(String, String, String, String, Long), User, NotUsed] =
    Slick.flowWithPassThrough(
      (UserDAO.createEmailAccount _).tupled
    )

  def usernameExists(username: String)(
    implicit
    db: Database
  ): Future[Boolean] =
    db.run(UserDAO.usernameExists(username))

  def usernameExists$()(
    implicit
    session: SlickSession
  ): Flow[String, Boolean, NotUsed] =
    Slick.flowWithPassThrough(UserDAO.usernameExists)
}

object UserDAO {
  private[this] lazy val getUserQuery = Compiled { (userId: Rep[UUID]) =>
    UserTable.table.filter(_.userId === userId)
  }

  private[dao] def getUser(userId: UUID): DBIO[Option[User]] =
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

  private[dao] def getUserByEmail(email: String): DBIO[Option[User]] =
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

  private[dao] def getUserByExt(
    extType: UserExtType,
    extId: String
  ): DBIO[Option[User]] =
    getUserByExtQuery(extType, extId).result.headOption

  private[this] lazy val usernameExistsQuery = Compiled { (username: Rep[String]) =>
    UserTable.table.filter(_.username === username).exists
  }

  private[dao] def usernameExists(username: String): DBIO[Boolean] =
    usernameExistsQuery(username).result

  private[dao] def upsertUser(user: User)(
    implicit
    ec: ExecutionContext
  ): DBIO[User] =
    user.userId.fold[DBIO[User]](
      (UserTable.table returning UserTable.table) += user.copy(userId = Some(UUID.randomUUID()))
    ) { (userId: UUID) => for {
      _ <- UserTable.table.update(user)
      updated <- UserDAO.getUser(userId)
    } yield updated.get}

  private[dao] def createEmailAccount(
    email: String,
    username: String,
    password: String,
    zipCode: String,
    providerId: Long,
  )(
    implicit
    ec: ExecutionContext
  ): DBIO[User] = (for {
    user <- upsertUser(User(
      userId = None,
      username = username,
      active = true,
      verified = false,
      role = UserRole.Registered
    ))
    _ <- UserMetaDAO.upsertUserMeta(UserMeta(
      userId = user.userId.get,
      email = email
    ))
    hashedPw <- DBIO.from(Future.fromTry(password.bcryptSafe(10)))
    _ <- UserPrivateDAO.upsertUserPrivate(UserPrivate(
      userId = user.userId.get,
      password = hashedPw
    ))
    _ <- UserProviderDAO.addUserProvider(UserProvider(
      userId = user.userId.get,
      zipCode = zipCode,
      providerId = providerId
    ))
  } yield user).transactionally
 }
