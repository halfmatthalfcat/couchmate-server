package com.couchmate.data.db.table

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.{CountryCode, ZipProvider}
import slick.lifted.{PrimaryKey, Tag}
import slick.migration.api._

class ZipProviderTable(tag: Tag) extends Table[ZipProvider](tag, "zip_provider") {
  def zipCode: Rep[String] = column[String]("zip_code")
  def countryCode: Rep[CountryCode] = column[CountryCode]("country_code")
  def providerId: Rep[Long] = column[Long]("provider_id")
  def * = (
    zipCode,
    countryCode,
    providerId,
  ) <> ((ZipProvider.apply _).tupled, ZipProvider.unapply)

  def zipProviderPk: PrimaryKey = primaryKey(
    "zip_provider_pk",
    (zipCode, countryCode, providerId)
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
      _.countryCode,
      _.providerId,
    ).addPrimaryKeys(
      _.zipProviderPk,
    ).addForeignKeys(
      _.providerFk,
    )
}
