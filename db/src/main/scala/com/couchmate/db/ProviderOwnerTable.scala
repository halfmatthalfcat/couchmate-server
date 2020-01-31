package com.couchmate.db

import com.couchmate.common.models.ProviderOwner
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class ProviderOwnerTable(tag: Tag) extends Table[ProviderOwner](tag, "provider_owner") {
  def providerOwnerId: Rep[Long] = column[Long]("provider_owner_id", O.PrimaryKey, O.AutoInc)
  def extProviderOwnerId: Rep[Option[String]] = column[Option[String]]("ext_provider_owner_id")
  def name: Rep[String] = column[String]("name")
  def * = (
    providerOwnerId.?,
    extProviderOwnerId,
    name,
  ) <> ((ProviderOwner.apply _).tupled, ProviderOwner.unapply)
}

object ProviderOwnerTable extends Slickable[ProviderOwnerTable] {
  val table = TableQuery[ProviderOwnerTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.providerOwnerId,
      _.extProviderOwnerId,
      _.name,
    )

//  private[this] lazy val getProviderOwnerCompiled = Compiled { (providerOwnerId: Rep[Long]) =>
//    providerOwnerTable.filter(_.providerOwnerId === providerOwnerId)
//  }
//
//  def getProviderOwner(
//    providerOwnerId: Long,
//  ): AppliedCompiledFunction[Long, Query[ProviderOwner, ProviderOwner, Seq], Seq[ProviderOwner]] = {
//    getProviderOwnerCompiled(providerOwnerId)
//  }
//
//  private[this] lazy val getProviderOwnerForNameCompiled = Compiled { (name: Rep[String]) =>
//    providerOwnerTable.filter(_.name === name)
//  }
//
//  def getProviderOwnerForName(name: String): AppliedCompiledFunction[String, Query[ProviderOwner, ProviderOwner, Seq], Seq[ProviderOwner]] = {
//    getProviderOwnerForNameCompiled(name)
//  }
//
//  private[this] lazy val getProviderOwnerForExtCompiled = Compiled { (extProviderOwnerId: Rep[String]) =>
//    providerOwnerTable.filter(_.extProviderOwnerId === extProviderOwnerId)
//  }
//
//  def getProviderOwnerForExt(extProviderOwnerId: String): AppliedCompiledFunction[String, Query[ProviderOwner, ProviderOwner, Seq], Seq[ProviderOwner]] = {
//    getProviderOwnerForExtCompiled(extProviderOwnerId)
//  }
//
//  def upsertProviderOwner(po: ProviderOwner): SqlStreamingAction[Vector[ProviderOwner], ProviderOwner, Effect] = {
//    sql"""
//         INSERT INTO provider_owner
//         (provider_owner_id, ext_provider_owner_id, name)
//         VALUES
//         (${po.providerOwnerId}, ${po.extProviderOwnerId}, ${po.name})
//         ON CONFLICT (provider_owner_id)
//         DO UPDATE SET
//            ext_provider_owner_id = ${po.extProviderOwnerId},
//            name = ${po.name}
//         RETURNING *
//       """.as[ProviderOwner]
//  }
}
