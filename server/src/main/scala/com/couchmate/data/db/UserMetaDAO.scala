package com.couchmate.data.db

import java.util.UUID

import com.couchmate.common.models.UserMeta

class UserMetaDAO()(
  implicit val ctx: CMContext
) {
  import ctx._

  def getUserMeta(userId: UUID) = quote {
    query[UserMeta]
      .filter(_.userId == userId)
  }

  def upsertMeta(userMeta: UserMeta) = quote {
    query[UserMeta]
      .insert(lift(userMeta))
      .onConflictUpdate(_.userId)(
        (from, to) => from.email -> to.email
      ).returning(um => um)
  }

}
