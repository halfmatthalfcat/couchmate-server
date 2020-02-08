package com.couchmate.data.db.table

import com.couchmate.data.db.Slickable
import com.couchmate.data.models.ZipProvider
import com.couchmate.data.db.Slickable
import com.couchmate.data.db.PgProfile.api._
import slick.lifted.{PrimaryKey, Tag}
import slick.migration.api._

class ZipProviderTable(tag: Tag) extends Table[ZipProvider](tag, "zip_provider") {
  def zipCode: Rep[String] = column[String]("zip_code")
  def providerId: Rep[Long] = column[Long]("provider_id")
  def * = (
    zipCode,
    providerId,
  ) <> ((ZipProvider.apply _).tupled, ZipProvider.unapply)

  def zipProviderPk: PrimaryKey = primaryKey(
    "zip_provider_pk",
    (zipCode, providerId)
  )

  def providerFk = foreignKey(
    "zip_provider_provider_fk",
    providerId,
    ProviderTable.table,
    )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object ZipProviderTable extends Slickable[ZipProviderTable] {
  private[db] val table = TableQuery[ZipProviderTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.zipCode,
      _.providerId,
    ).addPrimaryKeys(
      _.zipProviderPk,
    ).addForeignKeys(
      _.providerFk,
    )
}