package com.couchmate.db

import slick.migration.api.TableMigration

trait Slickable[T <: Table[_]] {
  val table: TableQuery[T]
  val schema: PgProfile.SchemaDescription
  val init: TableMigration[T, _]
}
