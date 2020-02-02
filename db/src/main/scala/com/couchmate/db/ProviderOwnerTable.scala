package com.couchmate.db

import com.couchmate.common.models.ProviderOwner
import com.couchmate.db.PgProfile.api._
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
  val table = TableQuery[ProviderOwnerTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.providerOwnerId,
      _.extProviderOwnerId,
      _.name,
    )
}
