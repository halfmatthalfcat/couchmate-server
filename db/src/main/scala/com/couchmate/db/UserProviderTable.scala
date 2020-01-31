package com.couchmate.db

import java.util.UUID

import com.couchmate.common.models.UserProvider
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class UserProviderTable(tag: Tag) extends Table[UserProvider](tag, "user_provider") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey)
  def zipCode: Rep[String] = column[String]("zip_code")
  def providerId: Rep[Long] = column[Long]("provider_id")
  def * = (
    userId,
    zipCode,
    providerId,
  ) <> ((UserProvider.apply _).tupled, UserProvider.unapply)

  def userFk = foreignKey(
    "user_provider_user_fk",
    userId,
    UserTable.table,
    )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )

  def providerFk = foreignKey(
    "user_provider_provider_fk",
    providerId,
    ProviderTable.table,
    )(
    _.providerId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserProviderTable extends Slickable[UserProviderTable] {
  val table = TableQuery[UserProviderTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.zipCode,
      _.providerId,
    ).addForeignKeys(
      _.userFk,
      _.providerFk,
    )

//  def getUserProvider(userId: UUID)(
//    implicit
//    db: Database
//  ): Future[Option[UserProvider]] = {
//    db.run(userProviderTable.filter(_.userId === userId).result.headOption)
//  }
//
//  def getProviders(userId: UUID)(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[Seq[Provider]] = {
//    db.run((for {
//      up <- userProviderTable
//      p <- Provider.providerTable if up.userId === userId
//    } yield p).result)
//  }
//
//  def userProviderExists(providerId: Long, zipCode: String)(
//    implicit
//    db: Database
//  ): Future[Boolean] = {
//    db.run(userProviderTable.filter { up =>
//      up.providerId === providerId &&
//      up.zipCode === zipCode
//    }.exists.result)
//  }
//
//  def upsertUserProvider(userProvider: UserProvider)(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[UserProvider] = {
//    db.run(
//      userProviderTable.filter { up =>
//        up.userId === userProvider.userId
//      }.exists.result
//    ) flatMap {
//      case true => for {
//        _ <- db.run(
//          userProviderTable.filter(_.userId === userProvider.userId).update(userProvider)
//        )
//        updatedUp <- db.run(
//          userProviderTable.filter(_.userId === userProvider.userId).result.head
//        )
//      } yield updatedUp
//      case false => db.run(
//        (userProviderTable returning userProviderTable) += userProvider
//      )
//    }
//  }
//
//  def getUniqueInternalProviders()(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[Seq[Long]] = {
//    db.run(userProviderTable.distinct.result).map(_.map(_.providerId))
//  }
//
//  def getUniqueProviders()(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[Seq[String]] = {
//    db.run((for {
//      p <- Provider.providerTable
//      up <- userProviderTable if p.providerId === up.providerId
//    } yield p.extId).distinct.result)
//  }
}
