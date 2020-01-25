package com.couchmate.data.schema

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.{Flow, Source}
import com.couchmate.data.models.UserPrivate
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

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

  val init = TableMigration(userPrivateTable)
    .create
    .addColumns(
      _.userId,
      _.password,
    ).addForeignKeys(
      _.userFk,
    )

  def getUserPrivate()(
    implicit
    session: SlickSession,
  ): Flow[UUID, Option[UserPrivate], NotUsed] = Slick.flowWithPassThrough { userId =>
    userPrivateTable.filter(_.userId === userId).result.headOption
  }

  def upsertUserPrivate()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[UserPrivate, UserPrivate, NotUsed] = Flow[UserPrivate].flatMapConcat {
    case userPrivate @ UserPrivate(userId, _) => Source
      .single(userId)
      .via(getUserPrivate())
      .via(Slick.flowWithPassThrough {
        case None =>
          (userPrivateTable returning userPrivateTable) += userPrivate
        case Some(_) => for {
          _ <- userPrivateTable.filter(_.userId === userPrivate.userId).update(userPrivate)
          up <- userPrivateTable.filter(_.userId === userPrivate.userId).result.head
        } yield up
      })
  }
}
