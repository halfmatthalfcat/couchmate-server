package com.couchmate.db.query

import java.util.UUID

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.UserPrivateTable

trait UserPrivateQueries {

  private[db] lazy val getUserPrivate = Compiled { (userId: Rep[UUID]) =>
    UserPrivateTable.table.filter(_.userId === userId)
  }

}
