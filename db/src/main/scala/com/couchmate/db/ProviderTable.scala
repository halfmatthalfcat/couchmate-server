package com.couchmate.db

import com.couchmate.common.models.Provider
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class ProviderTable(tag: Tag) extends Table[Provider](tag, "provider") {
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
    ProviderOwnerTable.table
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

object ProviderTable extends Slickable[ProviderTable] {
  val table = TableQuery[ProviderTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
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

//  private[this] lazy val getProviderCompiled = Compiled { (providerId: Rep[Long]) =>
//    providerTable.filter(_.providerId === providerId)
//  }
//
//  def getProvider(providerId: Long): AppliedCompiledFunction[Long, Query[ProviderTable, ProviderTable, Seq], Seq[ProviderTable]] = {
//    getProviderCompiled(providerId)
//  }
//
//  private[this] lazy val getProviderForExtAndOwnerCompiled = Compiled {
//    (extId: Rep[String], providerOwnerId: Rep[Option[Long]]) =>
//      providerTable.filter { provider =>
//        provider.providerOwnerId === providerOwnerId &&
//        provider.extId === extId
//      }
//  }
//
//  def getProviderForExtAndOwner(
//    extId: String,
//    providerOwnerId: Option[Long],
//  ): AppliedCompiledFunction[(String, Option[Long]), Query[ProviderTable, ProviderTable, Seq], Seq[ProviderTable]] = {
//    getProviderForExtAndOwnerCompiled(extId, providerOwnerId)
//  }
//
//  def upsertProvider(p: ProviderTable): SqlStreamingAction[Vector[ProviderTable], ProviderTable, Effect] = {
//    sql"""
//         INSERT INTO provider
//         (provider_id, ext_id, name, type, location)
//         VALUES
//         (${p.providerId}, ${p.extId}, ${p.name}, ${p.`type`}, ${p.location})
//         ON CONFLICT (provider_id)
//         DO UPDATE SET
//            ext_id = ${p.extId},
//            name = ${p.name},
//            type = ${p.`type`},
//            location = ${p.location}
//         RETURNING *
//       """.as[ProviderTable]
//  }
}
