package com.couchmate.data.db

trait Slickable[T <: Table[_]] {
  val table: TableQuery[T]
  val schema: PgProfile.SchemaDescription
  val init: TableMigration[T, _]
}
