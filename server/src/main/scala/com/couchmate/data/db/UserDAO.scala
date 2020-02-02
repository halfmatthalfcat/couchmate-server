package com.couchmate.data.db

import java.util.UUID

import com.couchmate.common.models.{User, UserExt, UserExtType, UserMeta}

class UserDAO()(
  implicit
  val ctx: CMContext,
) {
  import ctx._

  private[this] implicit val userInsertMeta =
    insertMeta[User](_.userId)

  def getUser(userId: UUID) = quote {
    query[User]
      .filter(_.userId.contains(userId))
  }

  def getUserByEmail(email: String) = quote {
    for {
      u <- query[User]
      um <- query[UserMeta] if (
        u.userId.contains(um.userId) &&
        um.email == email
      )
    } yield u
  }

  def getUserByExt(extType: UserExtType, extId: String) = quote {
    for {
      u <- query[User]
      ue <- query[UserExt] if (
        ue.extType == extType &&
        ue.extId == extId
      )
    } yield u
  }

  def upsertUser(user: User) = quote {
    query[User]
      .insert(lift(user))
      .onConflictUpdate(_.userId)(
        (from, to) => from.active -> to.active,
        (from, to) => from.verified -> to.verified,
        (from, to) => from.username -> to.username
      ).returning(u => u)
  }

}
