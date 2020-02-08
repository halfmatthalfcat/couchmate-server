package com.couchmate.data.db.query

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.UserMetaTable

trait UserMetaQueries {

  private[db] lazy val getUserMeta = Compiled { (userId: Rep[UUID]) =>
    UserMetaTable.table.filter(_.userId === userId)
  }

}
