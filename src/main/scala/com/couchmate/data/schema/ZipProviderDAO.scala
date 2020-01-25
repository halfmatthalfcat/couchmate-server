package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.{Provider, ZipProvider}
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.{PrimaryKey, Tag}
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class ZipProviderDAO(tag: Tag) extends Table[ZipProvider](tag, "zip_provider") {
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

  def getZipProvidersForZip()(
    implicit
    session: SlickSession,
  ): Flow[String, Seq[ZipProvider], NotUsed] = Slick.flowWithPassThrough { zipCode =>
    zipProviderTable.filter(_.zipCode === zipCode).result
  }

  def getProvidersForZip()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[String, Seq[Provider], NotUsed] = Slick.flowWithPassThrough { zipCode =>
    (for {
      zp <- zipProviderTable
      p <- ProviderDAO.providerTable
      if zp.providerId === p.providerId
    } yield p).result
  }

  def providerExistsForProviderAndZip()(
    implicit
    session: SlickSession,
  ): Flow[(Long, String), Boolean, NotUsed] = Slick.flowWithPassThrough {
    case (providerId, zipCode) => zipProviderTable.filter { zp =>
      zp.providerId === providerId &&
      zp.zipCode === zipCode
    }.exists.result
  }

  def insertZipProvider()(
    implicit
    session: SlickSession,
  ): Flow[ZipProvider, ZipProvider, NotUsed] = Slick.flowWithPassThrough { zipProvider =>
    (zipProviderTable returning zipProviderTable) += zipProvider
  }
}
