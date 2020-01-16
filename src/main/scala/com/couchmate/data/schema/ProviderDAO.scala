package com.couchmate.data.schema

import com.couchmate.data.models.Provider
import slick.jdbc.PostgresProfile.api._
import slick.lifted.Tag

import scala.concurrent.{ExecutionContext, Future}

class ProviderDAO(tag: Tag) extends Table[Provider](tag, "provider") {
  def providerId: Rep[Long] = column[Long]("provider_id", O.PrimaryKey, O.AutoInc)
  def sourceId: Rep[Long] = column[Long]("source_id")
  def extId: Rep[String] = column[String]("ext_id")
  def name: Rep[String] = column[String]("name")
  def `type`: Rep[Option[String]] = column[Option[String]]("type")
  def location: Rep[Option[String]] = column[Option[String]]("location")
  def * = (
    providerId.?,
    sourceId,
    extId,
    name,
    `type`,
    location,
  ) <> ((Provider.apply _).tupled, Provider.unapply)

  def sourceFK = foreignKey(
    "source_fk",
    sourceId,
    SourceDAO.sourceTable
  )(
    _.sourceId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict
  )
  def sourceIdx = index("source_idx", (sourceId, extId))
}

object ProviderDAO {
  val providerTable = TableQuery[ProviderDAO]

  def getProvider(providerId: Long)(
    implicit
    db: Database,
  ): Future[Option[Provider]] = {
    db.run(
      providerTable.filter(_.providerId === providerId).result.headOption
    )
  }

  def getProviderForSourceAndExt(sourceId: Long, extId: String)(
    implicit
    db: Database,
  ): Future[Option[Provider]] = {
    db.run(
      providerTable.filter { provider =>
        provider.sourceId === sourceId &&
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
