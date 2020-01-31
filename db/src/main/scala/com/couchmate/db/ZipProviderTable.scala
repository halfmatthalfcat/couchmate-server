package com.couchmate.db

import com.couchmate.common.models.ZipProvider
import com.couchmate.db.PgProfile.api._
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
  val table = TableQuery[ZipProviderTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.zipCode,
      _.providerId,
    ).addPrimaryKeys(
      _.zipProviderPk,
    ).addForeignKeys(
      _.providerFk,
    )

//  def getZipProvidersForZip(zipCode: String)(
//    implicit
//    db: Database,
//  ): Future[Seq[ZipProvider]] = {
//    db.run(zipProviderTable.filter(_.zipCode === zipCode).result)
//  }
//
//  def getProvidersForZip(zipCode: String)(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[Seq[Provider]] = {
//    db.run((for {
//      zp <- zipProviderTable
//      p <- Provider.providerTable
//        if zp.providerId === p.providerId
//    } yield p).result)
//  }
//
//  def providerExistsForProviderAndZip(providerId: Long, zipCode: String)(
//    implicit
//    db: Database,
//  ): Future[Boolean] = {
//    db.run(zipProviderTable.filter { zp =>
//      zp.providerId === providerId &&
//      zp.zipCode === zipCode
//    }.exists.result)
//  }
//
//  def insertZipProvider(zipProvider: ZipProvider)(
//    implicit
//    db: Database,
//  ): Future[ZipProvider] = {
//    db.run((zipProviderTable returning zipProviderTable) += zipProvider)
//  }
}
