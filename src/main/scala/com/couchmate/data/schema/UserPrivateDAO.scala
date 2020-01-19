package com.couchmate.data.schema

import java.util.UUID

import PgProfile.api._
import com.couchmate.data.models.UserPrivate
import slick.lifted.Tag

import scala.concurrent.{ExecutionContext, Future}

class UserPrivateDAO(tag: Tag) extends Table[UserPrivate](tag, "user_private") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def password: Rep[String] = column[String]("password")
  def * = (
    userId,
    password,
  ) <> ((UserPrivate.apply _).tupled, UserPrivate.unapply)

  def userFk = foreignKey(
    "user_user_private_fk",
    userId,
    UserDAO.userTable,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserPrivateDAO {
  val userPrivateTable = TableQuery[UserPrivateDAO]

  def getUserPrivate(userId: UUID)(
    implicit
    db: Database,
  ): Future[Option[UserPrivate]] = {
    db.run(userPrivateTable.filter(_.userId === userId).result.headOption)
  }

  def upsertUserPrivate(userPrivate: UserPrivate)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[UserPrivate] = {
    getUserPrivate(userPrivate.userId) flatMap {
      case None =>
        db.run((userPrivateTable returning userPrivateTable) += userPrivate)
      case Some(_) => for {
        _ <- db.run(userPrivateTable.filter(_.userId === userPrivate.userId).update(userPrivate))
        up <- db.run(userPrivateTable.filter(_.userId === userPrivate.userId).result.head)
      } yield up
    }
  }
}
