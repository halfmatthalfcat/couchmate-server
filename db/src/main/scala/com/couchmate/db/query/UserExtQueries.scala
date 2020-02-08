package com.couchmate.db.query

import java.util.UUID

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.UserExtTable

trait UserExtQueries {

  private[db] lazy val getUserExt = Compiled { (userId: Rep[UUID]) =>
    UserExtTable.table.filter(_.userId === userId)
  }

}
