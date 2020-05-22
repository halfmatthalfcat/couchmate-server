package com.couchmate.data.db

import com.couchmate.data.db.PgProfile.api._

trait DatabaseComponents {
  implicit lazy val db: Database =
    Database.forConfig("db")
}
