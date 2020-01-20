package com.couchmate.data.schema

import java.util.UUID

import PgProfile.api._
import com.couchmate.data.models.{UserExt, UserExtType}
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.Future

class UserExtDAO(tag: Tag) extends Table[UserExt](tag, "user_ext") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def extType: Rep[UserExtType] = column[UserExtType]("ext_type")
  def extId: Rep[String] = column[String]("ext_id")
  def * = (
    userId,
    extType,
    extId,
  ) <> ((UserExt.apply _).tupled, UserExt.unapply)

  def userFk = foreignKey(
    "user_user_ext_fk",
    userId,
    UserDAO.userTable,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserExtDAO {
  val userExtTable = TableQuery[UserExtDAO]

  val init = TableMigration(userExtTable)
    .create
    .addColumns(
      _.userId,
      _.extType,
      _.extId,
    ).addForeignKeys(
      _.userFk,
    )

  def getUserExt(userId: UUID)(
    implicit
    db: Database,
  ): Future[Option[UserExt]] = {
    db.run(userExtTable.filter(_.userId === userId).result.headOption)
  }

  def addUserExt(userExt: UserExt)(
    implicit
    db: Database,
  ): Future[UserExt] = {
    db.run((userExtTable returning userExtTable) += userExt)
  }
}
