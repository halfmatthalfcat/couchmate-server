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

  def userProviderExists(providerId: Long)(
    implicit
    db: Database
  ): Future[Boolean] =
    db.run(UserProviderDAO.userProviderExists(providerId))

  def userProviderExists$()(
    implicit
    session: SlickSession
  ): Flow[Long, Boolean, NotUsed] =
    Slick.flowWithPassThrough(UserProviderDAO.userProviderExists)

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

  private[db] def getUserProvider(userId: UUID): DBIO[Option[UserProvider]] =
    getUserProviderQuery(userId).result.headOption

  private[this] lazy val getProvidersQuery = Compiled { (userId: Rep[UUID]) =>
    for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( up.userId === userId )
    } yield p
  }

  private[db] def getProviders(userId: UUID): DBIO[Seq[Provider]] =
    getProvidersQuery(userId).result

  private[this] lazy val userProviderExistsQuery = Compiled {
    (providerId: Rep[Long]) =>
      UserProviderTable.table.filter { up =>
        up.providerId === providerId
      }.exists
  }

  private[db] def userProviderExists(providerId: Long): DBIO[Boolean] =
    userProviderExistsQuery(providerId).result

  private[this] lazy val getUniqueInternalProvidersQuery = Compiled {
    UserProviderTable.table.distinct
  }

  private[db] def getUniqueInternalProviders: StreamingDBIO[Seq[UserProvider], UserProvider] =
    getUniqueInternalProvidersQuery.result

  private[this] lazy val getUniqueProvidersQuery = Compiled {
    (for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( p.providerId === up.providerId )
    } yield p).distinct
  }

  private[db] def getUniqueProviders: StreamingDBIO[Seq[Provider], Provider] =
    getUniqueProvidersQuery.result

  private[db] def addUserProvider(userProvider: UserProvider): DBIO[UserProvider] =
    (UserProviderTable.table returning UserProviderTable.table) += userProvider
}
