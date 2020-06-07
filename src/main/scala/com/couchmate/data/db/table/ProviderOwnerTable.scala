package com.couchmate.data.db.table

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.dao.ProviderOwnerDAO
import com.couchmate.data.db.{PgProfile, Slickable}
import com.couchmate.data.models.ProviderOwner
import slick.lifted.Tag
import slick.migration.api._

import scala.concurrent.ExecutionContext

class ProviderOwnerTable(tag: Tag) extends Table[ProviderOwner](tag, "provider_owner") {
  def providerOwnerId: Rep[Long] = column[Long]("provider_owner_id", O.PrimaryKey, O.AutoInc)
  def extProviderOwnerId: Rep[Option[String]] = column[Option[String]]("ext_provider_owner_id", O.Unique)
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

  private[db] def seed(implicit ec: ExecutionContext): Option[DBIO[_]] = Some(DBIO.seq(
    ProviderOwnerDAO.upsertProviderOwner(ProviderOwner(
      providerOwnerId = None,
      extProviderOwnerId = None,
      name = "Default"
    ))
  ))
}
