package com.couchmate.common.util.slick

import com.couchmate.common.db.PgProfile.api._

trait WithTableQuery[T <: Table[_]] {
 private[couchmate] val table: TableQuery[T]
}
