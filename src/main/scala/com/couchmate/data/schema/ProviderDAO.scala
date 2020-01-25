package com.couchmate.data.schema

import akka.NotUsed
import akka.stream.alpakka.slick.scaladsl.{Slick, SlickSession}
import akka.stream.scaladsl.Flow
import com.couchmate.data.models.Provider
import com.couchmate.data.schema.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api.TableMigration

import scala.concurrent.ExecutionContext

class ProviderDAO(tag: Tag) extends Table[Provider](tag, "provider") {
  def providerId: Rep[Long] = column[Long]("provider_id", O.PrimaryKey, O.AutoInc)
  def sourceId: Rep[Long] = column[Long]("source_id")
  def extId: Rep[String] = column[String]("ext_id")
  def name: Rep[String] = column[String]("name")
  def `type`: Rep[String] = column[String]("type")
  def location: Rep[String] = column[String]("location")
  def * = (
    providerId.?,
    sourceId,
    extId,
    name,
    `type`.?,
    location.?,
  ) <> ((Provider.apply _).tupled, Provider.unapply)

  def sourceFK = foreignKey(
    "provider_source_fk",
    sourceId,
    SourceDAO.sourceTable
  )(
    _.sourceId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )

  def sourceIdx = index(
    "provider_source_idx",
    (sourceId, extId),
  )
}

object ProviderDAO {
  val providerTable = TableQuery[ProviderDAO]

  val init = TableMigration(providerTable)
    .create
    .addColumns(
      _.providerId,
      _.sourceId,
      _.extId,
      _.name,
      _.`type`,
      _.location,
    ).addForeignKeys(
      _.sourceFK,
    ).addIndexes(
      _.sourceIdx,
    )

  def getProvider()(
    implicit
    session: SlickSession,
  ): Flow[Long, Option[Provider], NotUsed] = Slick.flowWithPassThrough { providerId =>
    providerTable.filter(_.providerId === providerId).result.headOption
  }

  def getProviderForSourceAndExt()(
    implicit
    session: SlickSession,
  ): Flow[(Long, String), Option[Provider], NotUsed] = Slick.flowWithPassThrough {
    case (sourceId, extId) => providerTable.filter { provider =>
      provider.sourceId === sourceId &&
      provider.extId === extId
    }.result.headOption
  }

  def upsertProvider()(
    implicit
    session: SlickSession,
    ec: ExecutionContext,
  ): Flow[Provider, Provider, NotUsed] = Slick.flowWithPassThrough {
    case provider @ Provider(None, _, _, _, _, _) =>
      (providerTable returning providerTable) += provider
    case provider @ Provider(Some(providerId), _, _, _, _, _) =>
      for {
        _ <- providerTable.filter(_.providerId === providerId).update(provider)
        p <- providerTable.filter(_.providerId === providerId).result.head
      } yield p
  }
}
