package com.couchmate.data.db

import com.couchmate.data.db.PgProfile.api._
import slick.migration.api.TableMigration

trait Slickable[T <: Table[_]] {
  private[db] val table: TableQuery[T]
  private[db] val schema: PgProfile.SchemaDescription
  private[db] val init: TableMigration[T, _]
}
