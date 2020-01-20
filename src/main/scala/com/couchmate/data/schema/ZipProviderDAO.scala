package com.couchmate.data.schema

import PgProfile.api._
import com.couchmate.data.models.{Provider, ZipProvider}
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class ZipProviderDAO(tag: Tag) extends Table[ZipProvider](tag, "zip_provider") {
  def zipCode: Rep[String] = column[String]("zip_code")
  def providerId: Rep[Long] = column[Long]("provider_id")
  def * = (
    zipCode,
    providerId,
  ) <> ((ZipProvider.apply _).tupled, ZipProvider.unapply)

  def zipProviderPk = primaryKey(
    "zip_provider_pk",
    (zipCode, providerId)
  )

  def providerFk = foreignKey(
    "zip_provider_provider_fk",
    providerId,
    ProviderDAO.providerTable,
  )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object ZipProviderDAO {
  val zipProviderTable = TableQuery[ZipProviderDAO]

  val init = TableMigration(zipProviderTable)
    .create
    .addColumns(
      _.zipCode,
      _.providerId,
    ).addPrimaryKeys(
      _.zipProviderPk,
    ).addForeignKeys(
      _.providerFk,
    )

  def getZipProvidersForZip(zipCode: String)(
    implicit
    db: Database,
  ): Future[Seq[ZipProvider]] = {
    db.run(zipProviderTable.filter(_.zipCode === zipCode).result)
  }

  def getProvidersForZip(zipCode: String)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Seq[Provider]] = {
    db.run((for {
      zp <- zipProviderTable
      p <- ProviderDAO.providerTable
        if zp.providerId === p.providerId
    } yield p).result)
  }

  def providerExistsForProviderAndZip(providerId: Long, zipCode: String)(
    implicit
    db: Database,
  ): Future[Boolean] = {
    db.run(zipProviderTable.filter { zp =>
      zp.providerId === providerId &&
      zp.zipCode === zipCode
    }.exists.result)
  }

  def insertZipProvider(zipProvider: ZipProvider)(
    implicit
    db: Database,
  ): Future[ZipProvider] = {
    db.run((zipProviderTable returning zipProviderTable) += zipProvider)
  }
}
