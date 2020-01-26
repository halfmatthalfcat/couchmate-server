package com.couchmate.data.schema

import com.couchmate.data.models.Provider
import PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.{ExecutionContext, Future}

class ProviderDAO(tag: Tag) extends Table[Provider](tag, "provider") {
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
    ProviderOwnerDAO.providerOwnerTable
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

object ProviderDAO {
  val providerTable = TableQuery[ProviderDAO]

  val init = TableMigration(providerTable)
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

  def getProvider(providerId: Long)(
    implicit
    db: Database,
  ): Future[Option[Provider]] = {
    db.run(
      providerTable.filter(_.providerId === providerId).result.headOption
    )
  }

  def getProviderForExtAndOwner(extId: String, providerOwnerId: Option[Long])(
    implicit
    db: Database,
  ): Future[Option[Provider]] = {
    db.run(
      providerTable.filter { provider =>
        provider.providerOwnerId === providerOwnerId &&
        provider.extId === extId
      }.result.headOption
    )
  }

  def upsertProvider(provider: Provider)(
    implicit
    db: Database,
    ec: ExecutionContext,
  ): Future[Provider] = {
    provider match {
      case Provider(None, _, _, _, _, _) =>
        db.run((providerTable returning providerTable) += provider)
      case Provider(Some(providerId), _, _, _, _, _) =>
        for {
          _ <- db.run(providerTable.filter(_.providerId === providerId).update(provider))
          provider <- db.run(providerTable.filter(_.providerId === providerId).result.head)
        } yield provider
    }
  }
}
