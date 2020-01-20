package com.couchmate.data.schema

import java.util.UUID

import PgProfile.api._
import com.couchmate.data.models.UserMeta
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class UserMetaDAO(tag: Tag) extends Table[UserMeta](tag, "user_meta") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def email: Rep[String] = column[String]("email")
  def * = (
    userId,
    email,
  ) <> ((UserMeta.apply _).tupled, UserMeta.unapply)

  def userFk = foreignKey(
    "user_user_meta_fk",
    userId,
    UserDAO.userTable,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserMetaDAO {
  val userMetaTable = TableQuery[UserMetaDAO]

  val init = TableMigration(userMetaTable)
    .create
    .addColumns(
      _.userId,
      _.email,
    ).addForeignKeys(
      _.userFk,
    )

  def getUserMeta(userId: UUID)(
    implicit
    db: Database,
  ): Future[Option[UserMeta]] = {
    db.run(userMetaTable.filter(_.userId === userId).result.headOption)
  }

  def upsertUserMeta(userMeta: UserMeta)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[UserMeta] = {
    getUserMeta(userMeta.userId) flatMap {
      case None =>
        db.run((userMetaTable returning userMetaTable) += userMeta)
      case Some(userMeta) => for {
        _ <- db.run(userMetaTable.filter(_.userId === userMeta.userId).update(userMeta))
        um <- db.run(userMetaTable.filter(_.userId === userMeta.userId).result.head)
      } yield um
    }
  }
}
