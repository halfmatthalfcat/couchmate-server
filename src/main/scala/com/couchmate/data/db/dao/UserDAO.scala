package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.common.models.UserExtType
import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.query.UserQueries
import com.couchmate.data.db.table.UserTable
import com.couchmate.data.models.{User, UserExtType}

import scala.concurrent.{ExecutionContext, Future}

class UserDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) extends UserQueries {

  def getUser(userId: UUID): Future[Option[User]] = {
    db.run(super.getUser(userId).result.headOption)
  }

  def getUserByEmail(email: String): Future[Option[User]] = {
    db.run(super.getUserByEmail(email).result.headOption)
  }

  def getUserByExt(extType: UserExtType, extId: String): Future[Option[User]] = {
    db.run(super.getUserByExt(extType, extId).result.headOption)
  }

  def upsertUser(user: User): Future[User] =
    user.userId.fold(
      db.run((UserTable.table returning UserTable.table) += user)
    ) { (userId: UUID) => db.run(for {
      _ <- UserTable.table.update(user)
      updated <- super.getUser(userId)
    } yield updated.result.head.transactionally)}

}
