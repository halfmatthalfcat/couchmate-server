package com.couchmate.data.schema

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.{UserExt, UserExtType}
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

class UserExtDAO(tag: Tag) extends Table[UserExt](tag, "user_ext") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
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

  def getUserExt()(
    implicit
    session: SlickSession,
  ): Flow[UUID, Option[UserExt], NotUsed] = Slick.flowWithPassThrough { userId =>
    userExtTable.filter(_.userId === userId).result.headOption
  }

  def addUserExt()(
    implicit
    session: SlickSession,
  ): Flow[UserExt, UserExt, NotUsed] = Slick.flowWithPassThrough { userExt =>
    (userExtTable returning userExtTable) += userExt
  }
}
