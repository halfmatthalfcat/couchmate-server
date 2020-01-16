package com.couchmate.data.schema

import java.util.UUID

import com.couchmate.data.models.User
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import scala.concurrent.{ExecutionContext, Future}

class UserDAO(tag: Tag) extends Table[User](tag, "user") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
  def username: Rep[String] = column[String]("username")
  def active: Rep[Boolean] = column[Boolean]("active", O.Default(true))
  def verified: Rep[Boolean] = column[Boolean]("verified", O.Default(false))
  def * = (userId.?, username, active, verified) <> ((User.apply _).tupled, User.unapply)
}

object UserDAO {
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

  def getUser(userId: UUID)(implicit db: Database): Future[Option[User]] = {
    db.run(
      userTable.filter(_.userId === userId).result.headOption
    )
  }
}
