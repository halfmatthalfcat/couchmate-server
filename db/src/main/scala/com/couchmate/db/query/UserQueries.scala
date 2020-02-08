package com.couchmate.db.query

import java.util.UUID

import com.couchmate.common.models.UserExtType
import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.{UserExtTable, UserMetaTable, UserTable}

trait UserQueries {

  private[db] lazy val getUser = Compiled { (userId: Rep[UUID]) =>
    UserTable.table.filter(_.userId === userId)
  }

  private[db] lazy val getUserByEmail = Compiled { (email: Rep[String]) =>
    for {
      u <- UserTable.table
      um <- UserMetaTable.table if (
        u.userId === um.userId &&
        um.email === email
      )
    } yield u
  }

  private[db] lazy val getUserByExt = Compiled {
    (extType: Rep[UserExtType], extId: Rep[String]) =>
      for {
        u <- UserTable.table
        ue <- UserExtTable.table if (
          ue.extType === extType &&
          ue.extId === extId
        )
      } yield u
  }

}
