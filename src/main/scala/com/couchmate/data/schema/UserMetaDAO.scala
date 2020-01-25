package com.couchmate.data.schema

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.{Flow, Source}
import com.couchmate.data.models.UserMeta
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class UserMetaDAO(tag: Tag) extends Table[UserMeta](tag, "user_meta") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
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

  def getUserMeta()(
    implicit
    session: SlickSession,
  ): Flow[UUID, Option[UserMeta], NotUsed] = Slick.flowWithPassThrough { userId =>
    userMetaTable.filter(_.userId === userId).result.headOption
  }

  def upsertUserMeta()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[UserMeta, UserMeta, NotUsed] = Flow[UserMeta].flatMapConcat {
    case userMeta @ UserMeta(userId, _) => Source
      .single(userId)
      .via(getUserMeta())
      .via(Slick.flowWithPassThrough {
        case None =>
          (userMetaTable returning userMetaTable) += userMeta
        case Some(_) => for {
          _ <- userMetaTable.filter(_.userId === userMeta.userId).update(userMeta)
          um <- userMetaTable.filter(_.userId === userMeta.userId).result.head
        } yield um
      })
  }
}
