package com.couchmate.data.schema

import com.couchmate.data.models.Provider
import PgProfile.api._
import slick.lifted.{AppliedCompiledFunction, Tag}
import slick.migration.api.TableMigration
import slick.sql.SqlStreamingAction

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

  private[this] lazy val getProviderCompiled = Compiled { (providerId: Rep[Long]) =>
    providerTable.filter(_.providerId === providerId)
  }

  def getProvider(providerId: Long): AppliedCompiledFunction[Long, Query[ProviderDAO, Provider, Seq], Seq[Provider]] = {
    getProviderCompiled(providerId)
  }

  private[this] lazy val getProviderForExtAndOwnerCompiled = Compiled {
    (extId: Rep[String], providerOwnerId: Rep[Option[Long]]) =>
      providerTable.filter { provider =>
        provider.providerOwnerId === providerOwnerId &&
        provider.extId === extId
      }
  }

  def getProviderForExtAndOwner(
    extId: String,
    providerOwnerId: Option[Long],
  ): AppliedCompiledFunction[(String, Option[Long]), Query[ProviderDAO, Provider, Seq], Seq[Provider]] = {
    getProviderForExtAndOwnerCompiled(extId, providerOwnerId)
  }

  def upsertProvider(p: Provider): SqlStreamingAction[Vector[Provider], Provider, Effect] = {
    sql"""
         INSERT INTO provider
         (provider_id, ext_id, name, type, location)
         VALUES
         (${p.providerId}, ${p.extId}, ${p.name}, ${p.`type`}, ${p.location})
         ON CONFLICT (provider_id)
         DO UPDATE SET
            ext_id = ${p.extId},
            name = ${p.name},
            type = ${p.`type`},
            location = ${p.location}
         RETURNING *
       """.as[Provider]
  }
}
