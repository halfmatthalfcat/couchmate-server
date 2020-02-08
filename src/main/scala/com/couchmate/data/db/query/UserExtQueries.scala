package com.couchmate.data.db.query

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserExtTable

trait UserExtQueries {

  private[db] lazy val getUserExt = Compiled { (userId: Rep[UUID]) =>
    UserExtTable.table.filter(_.userId === userId)
  }

}
