package com.couchmate.db

import java.util.UUID

import com.couchmate.common.models.User
import com.couchmate.db.PgProfile.api._
import slick.lifted.Tag
import slick.migration.api._

class UserTable(tag: Tag) extends Table[User](tag, "user") {
  def userId: Rep[UUID] = column[UUID]("user_id", O.PrimaryKey, O.SqlType("uuid"))
  def username: Rep[String] = column[String]("username")
  def active: Rep[Boolean] = column[Boolean]("active", O.Default(true))
  def verified: Rep[Boolean] = column[Boolean]("verified", O.Default(false))
  def * = (
    userId.?,
    username,
    active,
    verified,
  ) <> ((User.apply _).tupled, User.unapply)
}

object UserTable extends Slickable[UserTable] {
  val table: TableQuery[UserTable] = TableQuery[UserTable]

  val schema: PgProfile.SchemaDescription = table.schema

  val init = TableMigration(table)
    .create
    .addColumns(
      _.userId,
      _.username,
      _.active,
      _.verified,
    )

//  private[this] lazy val getUserCompiled = Compiled { (userId: Rep[UUID]) =>
//    userTable.filter(_.userId === userId)
//  }
//
//  def getUser(userId: UUID): AppliedCompiledFunction[UUID, Query[UserTable, User, Seq], Seq[User]] = {
//    getUserCompiled(userId)
//  }
//
//  private[this] lazy val getUserByEmailCompiled = Compiled { (email: Rep[String]) =>
//    for {
//      u <- userTable
//      um <- UserMetaTable.userMetaTable
//      if  u.userId === um.userId &&
//          um.email === email
//    } yield u
//  }
//
//  def getUserByEmail(email: String): AppliedCompiledFunction[String, Query[UserTable, User, Seq], Seq[User]] = {
//    getUserByEmailCompiled(email)
//  }
//
//  private[this] lazy val getUserByExtCompiled = Compiled { (extType: Rep[UserExtType], extId: Rep[String]) =>
//    for {
//      u <- userTable
//      ue <- UserExtTable.userExtTable
//      if  ue.extType === extType &&
//          ue.extId === extId
//    } yield u
//  }
//
//  def getUserByExt(extType: UserExtType, extId: String): AppliedCompiledFunction[(UserExtType, String), Query[UserTable, User, Seq], Seq[User]] = {
//    getUserByExtCompiled(extType, extId)
//  }
//
//  def upsertUser(u: User) = {
//    sql"""
//         INSERT INTO "user"
//         (user_id, username, active, verified)
//         VALUES
//         (${u.userId}, ${u.username}, ${u.active}, ${u.verified})
//         ON CONFLICT (user_id)
//         DO UPDATE SET
//            username = ${u.username},
//            active = ${u.active},
//            verified = ${u.verified}
//         RETURNING *
//       """
//  }
//
//  def upsertUser(user: User)(
//    implicit
//    db: Database,
//    ec: ExecutionContext,
//  ): Future[User] = {
//    user match {
//      case User(None, _, _, _) =>
//        db.run((userTable returning userTable) += user.copy(userId = Some(UUID.randomUUID())))
//      case User(Some(userId), _, _, _) =>
//        for {
//          _ <- db.run(userTable.filter(_.userId === userId).update(user))
//          user <- db.run(userTable.filter(_.userId === userId).result.head)
//        } yield user
//    }
//  }
}
