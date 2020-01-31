package com.couchmate.db

import java.util.UUID

import com.couchmate.common.models.UserMeta
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class UserMetaTable(tag: Tag) extends Table[UserMeta](tag, "user_meta") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.SqlType("uuid"))
  def email: Rep[String] = column[String]("email")
  def * = (
    userId,
    email,
  ) <> ((UserMeta.apply _).tupled, UserMeta.unapply)

  def userFk = foreignKey(
    "user_user_meta_fk",
    userId,
    UserTable.table,
    )(
    _.userId,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Restrict,
  )
}

object UserMetaTable extends Slickable[UserMetaTable] {
  val table = TableQuery[UserMetaTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.email,
    ).addForeignKeys(
      _.userFk,
    )

//  def getUserMeta(userId: UUID)(
//    implicit
//    db: Database,
//  ): Future[Option[UserMeta]] = {
//    db.run(userMetaTable.filter(_.userId === userId).result.headOption)
//  }
//
//  def upsertUserMeta(userMeta: UserMeta)(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[UserMeta] = {
//    getUserMeta(userMeta.userId) flatMap {
//      case None =>
//        db.run((userMetaTable returning userMetaTable) += userMeta)
//      case Some(userMeta) => for {
//        _ <- db.run(userMetaTable.filter(_.userId === userMeta.userId).update(userMeta))
//        um <- db.run(userMetaTable.filter(_.userId === userMeta.userId).result.head)
//      } yield um
//    }
//  }
}
