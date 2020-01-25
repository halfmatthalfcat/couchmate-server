package com.couchmate.data.schema

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.{User, UserExtType}
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class UserDAO(tag: Tag) extends Table[User](tag, "user") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
  def username: Rep[String] = column[String]("username")
  def active: Rep[Boolean] = column[Boolean]("active", O.Default(true))
  def verified: Rep[Boolean] = column[Boolean]("verified", O.Default(false))
  def * = (userId.?, username, active, verified) <> ((User.apply _).tupled, User.unapply)
}

object UserDAO extends EnumMappers {
  val userTable = TableQuery[UserDAO]

  val init = TableMigration(userTable)
    .create
    .addColumns(
      _.userId,
      _.username,
      _.active,
      _.verified,
    )

  def getUser()(
    implicit
    session: SlickSession,
  ): Flow[UUID, Option[User], NotUsed] = Slick.flowWithPassThrough { userId =>
    userTable.filter(_.userId === userId).result.headOption
  }

  def getUserByEmail()(
    implicit
    session: SlickSession,
  ): Flow[String, Option[User], NotUsed] = Slick.flowWithPassThrough { email =>
    (for {
      u <- userTable
      um <- UserMetaDAO.userMetaTable
      if  u.userId === um.userId &&
        um.email === email
    } yield u).result.headOption
  }

  def getUserByExt()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[(UserExtType, String), Option[User], NotUsed] = Slick.flowWithPassThrough {
    case (extType, extId) => (for {
      u <- userTable
      ue <- UserExtDAO.userExtTable
      if  ue.extType === extType &&
          ue.extId === extId
    } yield u).result.headOption
  }

  def upsertUser()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[User, User, NotUsed] = Slick.flowWithPassThrough {
    case user @ User(None, _, _, _) =>
      (userTable returning userTable) += user.copy(userId = Some(UUID.randomUUID()))
    case user @ User(Some(userId), _, _, _) =>
      for {
        _ <- userTable.filter(_.userId === userId).update(user)
        user <- userTable.filter(_.userId === userId).result.head
      } yield user
  }
}
