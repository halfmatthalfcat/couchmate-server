package com.couchmate.data.db

import java.util.UUID

import com.couchmate.common.models.UserExt

class UserExtDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  def getUserExt(userId: UUID) = quote {
    query[UserExt]
      .filter(_.userId == userId)
  }

  def addUserExt(userExt: UserExt) = quote {
    query[UserExt]
      .insert(lift(userExt))
      .returning(ue => ue)
  }

}
