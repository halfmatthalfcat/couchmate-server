package com.couchmate.data.db.query

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{UserExtTable, UserMetaTable, UserTable}
import com.couchmate.data.models.UserExtType

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
