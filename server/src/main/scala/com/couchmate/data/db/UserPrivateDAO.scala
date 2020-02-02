package com.couchmate.data.db

import java.util.UUID

import com.couchmate.common.models.UserPrivate

class UserPrivateDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  def getUserPrivate(userId: UUID) = quote {
    query[UserPrivate]
      .filter(_.userId == userId)
  }

  def upsertUserProvider(userPrivate: UserPrivate) = quote {
    query[UserPrivate]
      .insert(lift(userPrivate))
      .onConflictUpdate(_.userId)(
        (from, to) => from.password -> to.password
      ).returning(up => up)
  }

}
