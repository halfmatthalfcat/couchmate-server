package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{ProviderTable, UserProviderTable}
import com.couchmate.data.models.{Provider, UserProvider}

import scala.concurrent.{ExecutionContext, Future}

trait UserProviderDAO {

  def getUserProvider(userId: UUID)(
    implicit
    db: Database
  ): Future[Option[UserProvider]] = {
    db.run(UserProviderDAO.getUserProvider(userId).result.headOption)
  }

  def getProviders(userId: UUID)(
    implicit
    db: Database
  ): Future[Seq[Provider]] = {
    db.run(UserProviderDAO.getProviders(userId).result)
  }

  def userProviderExists(providerId: Long, zipCode: String)(
    implicit
    db: Database
  ): Future[Boolean] = {
    db.run(UserProviderDAO.userProviderExists(providerId, zipCode).result)
  }

  def getInternalProvidersUnique()(
    implicit
    db: Database
  ): Future[Seq[UserProvider]] = {
    db.run(UserProviderDAO.getUniqueInternalProviders.result)
  }

  def getProvidersUnique()(
    implicit
    db: Database
  ): Future[Seq[String]] = {
    db.run(UserProviderDAO.getUniqueProviders.result)
  }

  def addUserProvider(userProvider: UserProvider)(
    implicit
    db: Database
  ): Future[UserProvider] = {
    db.run((UserProviderTable.table returning UserProviderTable.table) += userProvider)
  }

}

object UserProviderDAO {
  private[dao] lazy val getUserProvider = Compiled { (userId: Rep[UUID]) =>
    UserProviderTable.table.filter(_.userId === userId)
  }

  private[dao] lazy val getProviders = Compiled { (userId: Rep[UUID]) =>
    for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( up.userId === userId )
    } yield p
  }

  private[dao] lazy val userProviderExists = Compiled {
    (providerId: Rep[Long], zipCode: Rep[String]) =>
      UserProviderTable.table.filter { up =>
        up.providerId === providerId &&
          up.zipCode === zipCode
      }.exists
  }

  private[dao] lazy val getUniqueInternalProviders = Compiled {
    UserProviderTable.table.distinct
  }

  private[dao] lazy val getUniqueProviders = Compiled {
    (for {
      p <- ProviderTable.table
      up <- UserProviderTable.table if ( p.providerId === up.providerId )
    } yield p.extId).distinct
  }
}
