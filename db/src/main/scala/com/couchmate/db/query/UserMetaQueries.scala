package com.couchmate.db.query

import java.util.UUID

import com.couchmate.db.PgProfile.api._
import com.couchmate.db.table.UserMetaTable

trait UserMetaQueries {

  private[db] lazy val getUserMeta = Compiled { (userId: Rep[UUID]) =>
    UserMetaTable.table.filter(_.userId === userId)
  }

}
