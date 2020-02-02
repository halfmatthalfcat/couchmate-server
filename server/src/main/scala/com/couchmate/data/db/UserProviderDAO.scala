package com.couchmate.data.db

import java.util.UUID

import com.couchmate.common.models.{Provider, UserProvider}

class UserProviderDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  def getUserProvider(userId: UUID) = quote {
    query[UserProvider]
      .filter(_.userId == userId)
  }

  def getProviders(userId: UUID) = quote {
    for {
      p <- query[Provider]
      up <- query[UserProvider] if up.userId == userId
    } yield p
  }

  def userProviderExists(
    providerId: Long,
    zipCode: String,
  ) = quote {
    query[UserProvider]
      .filter { up =>
        up.providerId == providerId &&
        up.zipCode == zipCode
      }.nonEmpty
  }

  def getUniqueInternalProviders = quote {
    query[UserProvider].distinct
  }

  def getUniqueProviders = quote {
    (for {
      p <- query[Provider]
      up <- query[UserProvider] if p.providerId.contains(up.providerId)
    } yield p.extId).distinct
  }

  def upsertUserProvider(userProvider: UserProvider) = quote {
    query[UserProvider]
      .insert(lift(userProvider))
      .onConflictUpdate(_.userId)(
        (from, to) => from.providerId -> to.providerId,
        (from, to) => from.zipCode -> to.zipCode
      ).returning(up => up)
  }

}
