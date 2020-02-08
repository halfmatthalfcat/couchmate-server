package com.couchmate.data.db.table

import com.couchmate.data.db.Slickable
import com.couchmate.data.models.ProviderOwner
import com.couchmate.data.db.Slickable
import com.couchmate.data.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class ProviderOwnerTable(tag: Tag) extends Table[ProviderOwner](tag, "provider_owner") {
  def providerOwnerId: Rep[Long] = column[Long]("provider_owner_id", O.PrimaryKey, O.AutoInc)
  def extProviderOwnerId: Rep[Option[String]] = column[Option[String]]("ext_provider_owner_id")
  def name: Rep[String] = column[String]("name")
  def * = (
    providerOwnerId.?,
    extProviderOwnerId,
    name,
  ) <> ((ProviderOwner.apply _).tupled, ProviderOwner.unapply)
}

object ProviderOwnerTable extends Slickable[ProviderOwnerTable] {
  private[db] val table = TableQuery[ProviderOwnerTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.providerOwnerId,
      _.extProviderOwnerId,
      _.name,
    )
}