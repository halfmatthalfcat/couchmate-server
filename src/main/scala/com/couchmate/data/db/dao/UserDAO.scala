package com.couchmate.data.db.dao

import java.util.UUID

import com.couchmate.data.db.PgProfile.api._
import com.couchmate.data.db.table.{UserExtTable, UserMetaTable, UserTable}
import com.couchmate.data.models.{User, UserExtType}

import scala.concurrent.{ExecutionContext, Future}

class UserDAO(db: Database)(
  implicit
  ec: ExecutionContext,
) {

  def getUser(userId: UUID): Future[Option[User]] = {
    db.run(UserDAO.getUser(userId).result.headOption)
  }

  def getUserByEmail(email: String): Future[Option[User]] = {
    db.run(UserDAO.getUserByEmail(email).result.headOption)
  }

  def getUserByExt(extType: UserExtType, extId: String): Future[Option[User]] = {
    db.run(UserDAO.getUserByExt(extType, extId).result.headOption)
  }

  def upsertUser(user: User): Future[User] = db.run(
    user.userId.fold[DBIO[User]](
      (UserTable.table returning UserTable.table) += user.copy(userId = Some(UUID.randomUUID()))
    ) { (userId: UUID) => for {
      _ <- UserTable.table.update(user)
      updated <- UserDAO.getUser(userId).result.head
    } yield updated}.transactionally
  )
}

object UserDAO {
  private[dao] lazy val getUser = Compiled { (userId: Rep[UUID]) =>
    UserTable.table.filter(_.userId === userId)
  }

  private[dao] lazy val getUserByEmail = Compiled { (email: Rep[String]) =>
    for {
      u <- UserTable.table
      um <- UserMetaTable.table if (
        u.userId === um.userId &&
        um.email === email
      )
    } yield u
  }

  private[dao] lazy val getUserByExt = Compiled {
    (extType: Rep[UserExtType], extId: Rep[String]) =>
      for {
        u <- UserTable.table
        ue <- UserExtTable.table if (
          ue.extType === extType &&
          ue.extId === extId
        )
      } yield u
  }
}
