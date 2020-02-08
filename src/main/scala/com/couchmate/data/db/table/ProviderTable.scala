package com.couchmate.data.db.table

import com.couchmate.data.db.Slickable
import com.couchmate.data.models.Provider
import com.couchmate.data.db.Slickable
import com.couchmate.data.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class ProviderTable(tag: Tag) extends Table[Provider](tag, "provider") {
  def providerId: Rep[Long] = column[Long]("provider_id", O.PrimaryKey, O.AutoInc)
  def providerOwnerId: Rep[Long] = column[Long]("provider_owner_id")
  def extId: Rep[String] = column[String]("ext_id")
  def name: Rep[String] = column[String]("name")
  def `type`: Rep[String] = column[String]("type")
  def location: Rep[Option[String]] = column[Option[String]]("location")
  def * = (
    providerId.?,
    providerOwnerId.?,
    extId,
    name,
    `type`,
    location,
  ) <> ((Provider.apply _).tupled, Provider.unapply)

  def sourceFK = foreignKey(
    "provider_owner_fk",
    providerOwnerId,
    ProviderOwnerTable.table
    )(
    _.providerOwnerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def sourceIdx = index(
    "provider_owner_idx",
    (providerOwnerId, extId),
  )
}

object ProviderTable extends Slickable[ProviderTable] {
  private[db] val table = TableQuery[ProviderTable]

  private[db] val schema: PgProfile.SchemaDescription = table.schema

  private[db] val init = TableMigration(table)
    .create
    .addColumns(
      _.providerId,
      _.providerOwnerId,
      _.extId,
      _.name,
      _.`type`,
      _.location,
    ).addForeignKeys(
      _.sourceFK,
    )
}
