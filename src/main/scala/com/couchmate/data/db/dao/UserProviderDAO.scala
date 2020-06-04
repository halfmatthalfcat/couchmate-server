package com.couchmate.data.db.dao

import java.util.UUID

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.{Flow, Source}
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{ProviderTable, UserProviderTable}
import com.couchmate.data.models.{Provider, UserProvider}

import scala.concurrent.{ExecutionContext, Future}

trait UserProviderDAO {

  def getUserProvider(userId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserProvider]] =
    db.run(UserProviderDAO.getUserProvider(userId))

  def getUserProvider$()(
    implicit
    session: SlickSession
  ): Flow[UUID, Option[UserProvider], NotUsed] =
    Slick.flowWithPassThrough(UserProviderDAO.getUserProvider)

  def getProviders(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[Provider]] =
    db.run(UserProviderDAO.getProviders(userId))

  def getProviders$()(
    implicit
    session: SlickSession
  ): Flow[UUID, Seq[Provider], NotUsed] =
    Slick.flowWithPassThrough(UserProviderDAO.getProviders)

  def userProviderExists(providerId: Long, zipCode: String)(
    implicit
    db: Database
  ): Future[Boolean] =
    db.run(UserProviderDAO.userProviderExists(providerId, zipCode))

  def userProviderExists$()(
    implicit
    session: SlickSession
  ): Flow[(Long, String), Boolean, NotUsed] =
    Slick.flowWithPassThrough(
      (UserProviderDAO.userProviderExists _).tupled
    )

  def getUniqueInternalProviders()(
    implicit
    db: Database
  ): Future[Seq[UserProvider]] =
    db.run(UserProviderDAO.getUniqueInternalProviders)

  def getUniqueInternalProviders$()(
    implicit
    session: SlickSession
  ): Source[UserProvider, NotUsed] =
    Slick.source(UserProviderDAO.getUniqueInternalProviders)

  def getUniqueProviders()(
    implicit
    db: Database
  ): Future[Seq[Provider]] =
    db.run(UserProviderDAO.getUniqueProviders)

  def getUniqueProviders$()(
    implicit
    session: SlickSession
  ): Source[Provider, NotUsed] =
    Slick.source(UserProviderDAO.getUniqueProviders)

  def addUserProvider(userProvider: UserProvider)(
    implicit
    db: Database
  ): Future[UserProvider] =
    db.run(UserProviderDAO.addUserProvider(userProvider))

  def addUserProvider$()(
    implicit
    session: SlickSession
  ): Flow[UserProvider, UserProvider, NotUsed] =
    Slick.flowWithPassThrough(UserProviderDAO.addUserProvider)
}

object UserProviderDAO {
  private[this] lazy val getUserProviderQuery = Compiled { (userId: Rep[UUID]) =>
    UserProviderTable.table.filter(_.userId === userId)
  }

  private[dao] def getUserProvider(userId: UUID): DBIO[Option[UserProvider]] =
    getUserProviderQuery(userId).result.headOption

  private[this] lazy val getProvidersQuery = Compiled { (userId: Rep[UUID]) =>
    for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( up.userId === userId )
    } yield p
  }

  private[dao] def getProviders(userId: UUID): DBIO[Seq[Provider]] =
    getProvidersQuery(userId).result

  private[this] lazy val userProviderExistsQuery = Compiled {
    (providerId: Rep[Long], zipCode: Rep[String]) =>
      UserProviderTable.table.filter { up =>
        up.providerId === providerId &&
        up.zipCode === zipCode
      }.exists
  }

  private[dao] def userProviderExists(
    providerId: Long,
    zipCode: String
  ): DBIO[Boolean] =
    userProviderExistsQuery(providerId, zipCode).result

  private[this] lazy val getUniqueInternalProvidersQuery = Compiled {
    UserProviderTable.table.distinct
  }

  private[dao] def getUniqueInternalProviders: StreamingDBIO[Seq[UserProvider], UserProvider] =
    getUniqueInternalProvidersQuery.result

  private[this] lazy val getUniqueProvidersQuery = Compiled {
    (for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( p.providerId === up.providerId )
    } yield p).distinct
  }

  private[dao] def getUniqueProviders: StreamingDBIO[Seq[Provider], Provider] =
    getUniqueProvidersQuery.result

  private[dao] def addUserProvider(userProvider: UserProvider): DBIO[UserProvider] =
    (UserProviderTable.table returning UserProviderTable.table) += userProvider
}
