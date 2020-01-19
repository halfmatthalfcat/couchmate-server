package com.couchmate.data.schema

import java.util.UUID

import com.couchmate.data.models.{User, UserExtType}
import PgProfile.api._
import slick.lifted.Tag

import scala.concurrent.{ExecutionContext, Future}

class UserDAO(tag: Tag) extends Table[User](tag, "user") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
  def username: Rep[String] = column[String]("username")
  def active: Rep[Boolean] = column[Boolean]("active", O.Default(true))
  def verified: Rep[Boolean] = column[Boolean]("verified", O.Default(false))
  def * = (userId.?, username, active, verified) <> ((User.apply _).tupled, User.unapply)
}

object UserDAO extends EnumMappers {
  val userTable = TableQuery[UserDAO]

  def upsertUser(user: User)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[User] = {
    user match {
      case User(None, _, _, _) =>
        db.run((userTable returning userTable) += user.copy(userId = Some(UUID.randomUUID())))
      case User(Some(userId), _, _, _) =>
        for {
          _ <- db.run(userTable.filter(_.userId === userId).update(user))
          user <- db.run(userTable.filter(_.userId === userId).result.head)
        } yield user
    }
  }

  def getUser(userId: UUID)(
    implicit
    db: Database,
  ): Future[Option[User]] = {
    db.run(
      userTable.filter(_.userId === userId).result.headOption
    )
  }

  def getUserByEmail(email: String)(
    implicit
    db: Database,
  ): Future[Option[User]] = {
    db.run((for {
      u <- userTable
      um <- UserMetaDAO.userMetaTable
      if  u.userId === um.userId &&
          um.email === email
    } yield u).result.headOption)
  }

  def getUserByExt(extType: UserExtType, extId: String)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Option[User]] = {
    db.run((for {
      u <- userTable
      ue <- UserExtDAO.userExtTable
      if  ue.extType === extType &&
          ue.extId === extId
    } yield u).result.headOption)
  }
}
