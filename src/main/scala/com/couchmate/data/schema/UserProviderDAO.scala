package com.couchmate.data.schema

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.{Flow, Source}
import com.couchmate.data.models.{Provider, UserProvider}
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class UserProviderDAO(tag: Tag) extends Table[UserProvider](tag, "user_provider") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey)
  def zipCode: Rep[String] = column[String]("zip_code")
  def providerId: Rep[Long] = column[Long]("provider_id")
  def * = (userId, zipCode, providerId) <> ((UserProvider.apply _).tupled, UserProvider.unapply)

  def userFk = foreignKey(
    "user_provider_user_fk",
    userId,
    UserDAO.userTable,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def providerFk = foreignKey(
    "user_provider_provider_fk",
    providerId,
    ProviderDAO.providerTable,
  )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserProviderDAO {
  val userProviderTable = TableQuery[UserProviderDAO]

  val init = TableMigration(userProviderTable)
    .create
    .addColumns(
      _.userId,
      _.zipCode,
      _.providerId,
    ).addForeignKeys(
      _.userFk,
      _.providerFk,
    )

  def getUserProvider()(
    implicit
    session: SlickSession,
  ): Flow[UUID, Option[UserProvider], NotUsed] = Slick.flowWithPassThrough { userId =>
    userProviderTable.filter(_.userId === userId).result.headOption
  }

  def getProviders()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[UUID, Seq[Provider], NotUsed] = Slick.flowWithPassThrough { userId =>
    (for {
      up <- userProviderTable
      p <- ProviderDAO.providerTable if up.userId === userId
    } yield p).result
  }

  def userProviderExists()(
    implicit
    session: SlickSession,
  ): Flow[(Long, String), Boolean, NotUsed] = Slick.flowWithPassThrough {
    case (providerId, zipCode) => userProviderTable.filter { up =>
      up.providerId === providerId &&
      up.zipCode === zipCode
    }.exists.result
  }

  def upsertUserProvider()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[UserProvider, UserProvider, NotUsed] = Flow[UserProvider].flatMapConcat {
    case userProvider @ UserProvider(userId, _, _) => Source
      .single(userId)
      .via(getUserProvider())
      .via(Slick.flowWithPassThrough {
        case None =>
          (userProviderTable returning userProviderTable) += userProvider
        case Some(_) => for {
          _ <- userProviderTable.filter(_.userId === userProvider.userId).update(userProvider)
          updatedUp <- userProviderTable.filter(_.userId === userProvider.userId).result.head
        } yield updatedUp
      })
  }

  def getUniqueInternalProviders()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Source[Seq[Long], NotUsed] = Slick
    .source(userProviderTable.distinct.result)
    .map(_.providerId)
    .fold(Seq[Long]())(_ :+ _)

  def getUniqueProviders()(
    implicit
    session: SlickSession,
  ): Source[Seq[String], NotUsed] = Slick
    .source((for {
      p <- ProviderDAO.providerTable
      up <- userProviderTable if p.providerId === up.providerId
    } yield p.extId).distinct.result)
    .fold(Seq[String]())(_ :+ _)
}
