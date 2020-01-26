package com.couchmate.data.schema

import com.couchmate.data.models.ProviderOwner
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class ProviderOwnerDAO(tag: Tag) extends Table[ProviderOwner](tag, "provider_owner") {
  def providerOwnerId: Rep[Long] = column[Long]("provider_owner_id", O.PrimaryKey, O.AutoInc)
  def extProviderOwnerId: Rep[Option[String]] = column[Option[String]]("ext_provider_owner_id")
  def name: Rep[String] = column[String]("name")
  def * = (
    providerOwnerId.?,
    extProviderOwnerId,
    name,
  ) <> ((ProviderOwner.apply _).tupled, ProviderOwner.unapply)
}

object ProviderOwnerDAO {
  val providerOwnerTable = TableQuery[ProviderOwnerDAO]

  val init = TableMigration(providerOwnerTable)
    .create
    .addColumns(
      _.providerOwnerId,
      _.extProviderOwnerId,
      _.name,
    )

  def getProviderOwner(
    providerOwnerId: Long,
  )(
    implicit
    db: Database,
  ): Future[Option[ProviderOwner]] = {
    db.run(providerOwnerTable.filter(_.providerOwnerId === providerOwnerId).result.headOption)
  }

  def getProviderOwnerForName(name: String)(
    implicit
    db: Database,
  ): Future[Option[ProviderOwner]] = {
    db.run(providerOwnerTable.filter(_.name === name).result.headOption)
  }

  def getProviderOwnerForExt(extProviderOwnerId: String)(
    implicit
    db: Database,
  ): Future[Option[ProviderOwner]] = {
    db.run(providerOwnerTable.filter(_.extProviderOwnerId === extProviderOwnerId).result.headOption)
  }

  def upsertProviderOwner(
    providerOwner: ProviderOwner,
  )(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[ProviderOwner] = {
    providerOwner match {
      case ProviderOwner(None, _, _) =>
        db.run(providerOwnerTable returning providerOwnerTable += providerOwner)
      case ProviderOwner(Some(providerOwnerId), _, _) => for {
        _ <- db.run(providerOwnerTable.filter(_.providerOwnerId === providerOwnerId).update(providerOwner))
        po <- db.run(providerOwnerTable.filter(_.providerOwnerId === providerOwnerId).result.head)
      } yield po
    }
  }
}
