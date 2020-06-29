package com.couchmate.common.tables

import com.couchmate.common.db.PgProfile.api._
import com.couchmate.common.models.data.ZipProvider
import com.couchmate.common.util.slick.WithTableQuery
import com.neovisionaries.i18n.CountryCode
import slick.lifted.PrimaryKey

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

object ZipProviderTable extends WithTableQuery[ZipProviderTable] {
  private[couchmate] val table = TableQuery[ZipProviderTable]
}
