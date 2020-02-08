package com.couchmate.data.db.query

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserPrivateTable

trait UserPrivateQueries {

  private[db] lazy val getUserPrivate = Compiled { (userId: Rep[UUID]) =>
    UserPrivateTable.table.filter(_.userId === userId)
  }

}
