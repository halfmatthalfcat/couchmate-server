package com.couchmate.data.schema

import java.util.UUID

import com.couchmate.data.models.{Provider, UserProvider}
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import scala.concurrent.{ExecutionContext, Future}

class UserProviderDAO(tag: Tag) extends Table[UserProvider](tag, "user_provider") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey)
  def zipCode: Rep[String] = column[String]("zip_code")
  def providerId: Rep[Long] = column[Long]("provider_id")
  def * = (userId, zipCode, providerId) <> ((UserProvider.apply _).tupled, UserProvider.unapply)

  def userFK = foreignKey(
    "user_fk",
    userId,
    UserDAO.userTable,
  )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def providerFK = foreignKey(
    "provider_fk",
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

  def getUserProvider(userId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserProvider]] = {
    db.run(userProviderTable.filter(_.userId === userId).result.headOption)
  }

  def getProviders(userId: UUID)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Seq[Provider]] = {
    db.run((for {
      up <- userProviderTable
      p <- ProviderDAO.providerTable if up.userId === userId
    } yield p).result)
  }

  def userProviderExists(providerId: Long, zipCode: String)(
    implicit
    db: Database
  ): Future[Boolean] = {
    db.run(userProviderTable.filter { up =>
      up.providerId === providerId &&
      up.zipCode === zipCode
    }.exists.result)
  }

  // TODO upsert
  // TODO get unique internal/external
}
