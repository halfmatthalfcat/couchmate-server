package com.couchmate.data.db

import com.couchmate.data.db.PgProfile.api._
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

trait Slickable[T <: Table[_]] {
  private[db] val table: TableQuery[T]
  private[db] val schema: PgProfile.SchemaDescription
  private[db] val init: TableMigration[T, _]
  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]]
}
