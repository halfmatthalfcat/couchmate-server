package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.ProviderOwner
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class ProviderOwnerDAO(tag: Tag) extends Table[ProviderOwner](tag, "provider_owner") {
  def providerOwnerId: Rep[Long] = column[Long]("provider_owner_id", O.PrimaryKey, O.AutoInc)
  def sourceId: Rep[Long] = column[Long]("source_id")
  def extProviderOwnerId: Rep[Long] = column[Long]("ext_provider_owner_id")
  def name: Rep[String] = column[String]("name")
  def * = (
    providerOwnerId.?,
    sourceId,
    extProviderOwnerId,
    name,
  ) <> ((ProviderOwner.apply _).tupled, ProviderOwner.unapply)

  def sourceFk = foreignKey(
    "provider_owner_source_fk",
    sourceId,
    SourceDAO.sourceTable,
  )(
    _.sourceId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def sourceIdx = index(
    "provider_owner_ext_idx",
    (sourceId, extProviderOwnerId),
    unique = true
  )
}

object ProviderOwnerDAO {
  val providerOwnerTable = TableQuery[ProviderOwnerDAO]

  val init = TableMigration(providerOwnerTable)
    .create
    .addColumns(
      _.providerOwnerId,
      _.sourceId,
      _.extProviderOwnerId,
      _.name,
    )
    .addForeignKeys(
      _.sourceFk,
    )
    .addIndexes(
      _.sourceIdx
    )

  def getProviderOwner()(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[ProviderOwner], NotUsed] = Slick.flowWithPassThrough { providerOwnerId =>
    providerOwnerTable.filter(_.providerOwnerId === providerOwnerId).result.headOption
  }

  def getProviderOwnerForSourceAndExt()(
    implicit
    session: SlickSession,
  ): Flow[(Long, Long), Option[ProviderOwner], NotUsed] = Slick.flowWithPassThrough {
    case (sourceId, extProviderOwnerId) => providerOwnerTable
      .filter { providerOwner =>
        providerOwner.sourceId === sourceId &&
        providerOwner.extProviderOwnerId === extProviderOwnerId
      }.result.headOption
  }

  def upsertProviderOwner()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[ProviderOwner, ProviderOwner, NotUsed] = Slick.flowWithPassThrough {
    case providerOwner @ ProviderOwner(None, _, _, _) =>
      (providerOwnerTable returning providerOwnerTable) += providerOwner
    case providerOwner @ ProviderOwner(Some(providerOwnerId), _, _, _) => for {
      _ <- providerOwnerTable.filter(_.providerOwnerId === providerOwnerId).update(providerOwner)
      po <- providerOwnerTable.filter(_.providerOwnerId === providerOwnerId).result.head
    } yield po
  }
}
