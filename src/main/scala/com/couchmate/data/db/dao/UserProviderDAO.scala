package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.common.models.UserProvider
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.UserProviderQueries
import com.couchmate.data.db.table.UserProviderTable
import com.couchmate.data.models.{Provider, UserProvider}

import scala.concurrent.{ExecutionContext, Future}

class UserProviderDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends UserProviderQueries {

  def getUserProvider(userId: UUID): Future[Option[UserProvider]] = {
    db.run(super.getUserProvider(userId).result.headOption)
  }

  def getProviders(userId: UUID): Future[Seq[Provider]] = {
    db.run(super.getProviders(userId).result)
  }

  def userProviderExists(providerId: Long, zipCode: String): Future[Boolean] = {
    db.run(super.userProviderExists(providerId, zipCode).result)
  }

  def getInternalProvidersUnique: Future[Seq[UserProvider]] = {
    db.run(super.getUniqueInternalProviders.result)
  }

  def getProvidersUnique: Future[Seq[String]] = {
    db.run(super.getUniqueProviders.result)
  }

  def addUserProvider(userProvider: UserProvider): Future[UserProvider] = {
    db.run((UserProviderTable.table returning UserProviderTable.table) += userProvider)
  }

}
